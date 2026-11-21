package unix.verse.p2p.net;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * A peer found on the local network that is ready to accept a transport connection.
 *
 * @param identity identity presented by the peer; never {@code null}
 * @param transportAddress address on which the peer transport is listening; never {@code null}
 * @author Damian Daniel
 * @since 0.1.0
 */
public record DiscoveredPeer(
    PeerIdentity identity,
    InetSocketAddress transportAddress
) {
  /**
   * Creates a discovered peer.
   *
   * @param identity identity presented by the peer; must not be {@code null}
   * @param transportAddress address on which the peer transport is listening; must not be
   *     {@code null}
   * @throws NullPointerException if {@code identity} or {@code transportAddress} is
   *     {@code null}
   */
  public DiscoveredPeer {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(transportAddress, "transportAddress");
  }
}
