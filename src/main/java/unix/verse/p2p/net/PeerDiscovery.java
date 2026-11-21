package unix.verse.p2p.net;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Finds peers in the same session on the local network.
 *
 * @author Damian Daniel
 * @since 0.1.0
 */
public interface PeerDiscovery extends AutoCloseable {
  /**
   * Starts advertising this peer and listening for peers in the same session.
   *
   * @param localIdentity identity to advertise; must not be {@code null}
   * @param transportAddress address at which this peer accepts transport connections; must not
   *     be {@code null}
   * @param onPeer called once when a peer is first discovered or its address changes; must not
   *     block and must not be {@code null}
   * @return a future that completes when discovery started, or fails with the socket bind error;
   *     never {@code null}
   * @throws NullPointerException if an argument is {@code null}
   */
  CompletableFuture<Void> start(
      PeerIdentity localIdentity,
      InetSocketAddress transportAddress,
      Consumer<DiscoveredPeer> onPeer);

  /**
   * Stops advertising and listening. Does nothing if discovery is already closed.
   */
  @Override
  void close();
}
