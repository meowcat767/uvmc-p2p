package unix.verse.p2p.net;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Links peer discovery to a transport and automatically connects discovered peers.
 *
 * @author Damian Daniel
 * @since 0.1.0
 */
public final class PeerNetwork implements AutoCloseable {
  private static final System.Logger LOGGER =
      System.getLogger(PeerNetwork.class.getName());

  private final PeerTransport transport;
  private final PeerDiscovery discovery;
  private final AtomicBoolean started = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final ConcurrentMap<PeerIdentity, CompletableFuture<PeerConnection>> outgoing =
      new ConcurrentHashMap<>();

  private volatile Consumer<PeerConnection> onConnection;

  /**
   * Creates a network that connects discovery results through a transport.
   *
   * @param transport transport used to listen and connect; must not be {@code null}
   * @param discovery discovery implementation used to find peers; must not be {@code null}
   * @throws NullPointerException if {@code transport} or {@code discovery} is {@code null}
   */
  public PeerNetwork(PeerTransport transport, PeerDiscovery discovery) {
    this.transport = Objects.requireNonNull(transport, "transport");
    this.discovery = Objects.requireNonNull(discovery, "discovery");
  }

  /**
   * Starts the transport, discovery and automatic connection handling.
   *
   * @param bindAddress local QUIC address to listen on; use port {@code 0} to select a free port;
   *     must not be {@code null}
   * @param onConnection called for inbound and outbound peer connections; must not block and
   *     must not be {@code null}
   * @return a future with the address actually bound by the transport; never {@code null}
   * @throws NullPointerException if an argument is {@code null}
   * @throws IllegalStateException if the network is closed or already started
   */
  public CompletableFuture<InetSocketAddress> start(
      InetSocketAddress bindAddress,
      Consumer<PeerConnection> onConnection) {
    Objects.requireNonNull(bindAddress, "bindAddress");
    Objects.requireNonNull(onConnection, "onConnection");
    CompletableFuture<InetSocketAddress> result = new CompletableFuture<>();

    if (closed.get())
      throw new IllegalStateException("Peer network is closed");
    if (!started.compareAndSet(false, true))
      throw new IllegalStateException("Peer network is already started");

    this.onConnection = onConnection;
    transport.listen(bindAddress, this::deliverInbound)
        .whenComplete((boundAddress, listenError) -> {
          if (listenError != null) {
            started.set(false);
            result.completeExceptionally(listenError);
            return;
          }
          discovery.start(transport.identity(), boundAddress, this::connectDiscovered)
              .whenComplete((nothing, discoveryError) -> {
                if (discoveryError != null) {
                  transport.close();
                  closed.set(true);
                  result.completeExceptionally(discoveryError);
                  return;
                }
                result.complete(boundAddress);
              });
        });
    return result;
  }

  /**
   * Returns the transport identity presented to discovered peers.
   *
   * @return the local peer identity; never {@code null}
   */
  public PeerIdentity identity() {
    return transport.identity();
  }

  /**
   * Closes discovery and the transport. Does nothing if already closed.
   */
  @Override
  public void close() {
    if (!closed.compareAndSet(false, true))
      return;
    discovery.close();
    transport.close();
  }

  private void deliverInbound(PeerConnection connection) {
    deliver(connection);
  }

  private void connectDiscovered(DiscoveredPeer peer) {
    if (peer.identity().equals(transport.identity()))
      return;

    CompletableFuture<PeerConnection> pending = new CompletableFuture<>();
    CompletableFuture<PeerConnection> previous =
        outgoing.putIfAbsent(peer.identity(), pending);
    if (previous != null)
      return;

    transport.connect(peer.transportAddress()).whenComplete((connection, error) -> {
      if (error != null) {
        outgoing.remove(peer.identity(), pending);
        pending.completeExceptionally(error);
        LOGGER.log(
            System.Logger.Level.INFO,
            "Could not connect to discovered peer {0}: {1}",
            peer.identity().peerId(),
            error);
        return;
      }
      pending.complete(connection);
      connection.closeFuture().whenComplete((nothing, closeError) ->
          outgoing.remove(peer.identity(), pending));
      deliver(connection);
    });
  }

  private void deliver(PeerConnection connection) {
    try {
      onConnection.accept(connection);
    } catch (RuntimeException error) {
      LOGGER.log(System.Logger.Level.WARNING, "Peer connection listener failed", error);
      connection.close();
    }
  }
}
