package unix.verse.p2p.net;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Opens and accepts connections to other peers. Implementations hide how peers reach each
 * other, so discovery, port mapping, hole punching and relaying can be swapped in behind this
 * interface.
 *
 * @author Damian Daniel
 * @since 0.1.0
 */
public interface PeerTransport extends AutoCloseable {
  /**
   * Returns the identity this transport presents to other peers.
   *
   * @return the local identity; never {@code null}
   */
  PeerIdentity identity();

  /**
   * Starts accepting connections. Binds a socket and keeps it open until {@link #close()}.
   * Only peers that pass the handshake are reported.
   *
   * @param bindAddress the local address to bind; use port {@code 0} to pick a free port;
   *     must not be {@code null}
   * @param onConnection called on a transport thread for each accepted peer and must not
   *     block; must not be {@code null}
   * @return a future with the address that was actually bound, or the bind error; never
   *     {@code null}
   * @throws NullPointerException if an argument is {@code null}
   */
  CompletableFuture<InetSocketAddress> listen(
      InetSocketAddress bindAddress, Consumer<PeerConnection> onConnection);

  /**
   * Connects to a remote peer and performs the handshake. May open a local socket on first
   * use.
   *
   * @param remoteAddress where the remote peer listens; must not be {@code null}
   * @return a future with the connection once the handshake succeeded, or a
   *     {@link HandshakeException} or transport error; never {@code null}
   * @throws NullPointerException if {@code remoteAddress} is {@code null}
   */
  CompletableFuture<PeerConnection> connect(InetSocketAddress remoteAddress);

  /**
   * Closes every socket and connection and stops all transport threads. Later calls to
   * {@link #listen} and {@link #connect} return failed futures. Does nothing if already closed.
   */
  @Override
  void close();
}
