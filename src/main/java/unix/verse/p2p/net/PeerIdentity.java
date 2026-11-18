package unix.verse.p2p.net;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies a peer, the session it belongs to and the protocol version it speaks.
 *
 * @param peerId unique ID of the peer; never {@code null}
 * @param sessionId ID of the session the peer belongs to; never {@code null}
 * @param protocolVersion protocol version the peer speaks
 * @author Damian Daniel
 * @since 0.1.0
 */
public record PeerIdentity(
    UUID peerId,
    UUID sessionId,
    int protocolVersion
) {
  /**
   * Creates an identity.
   *
   * @param peerId unique ID of the peer; must not be {@code null}
   * @param sessionId ID of the session the peer belongs to; must not be {@code null}
   * @param protocolVersion protocol version the peer speaks
   * @throws NullPointerException if {@code peerId} or {@code sessionId} is {@code null}
   */
  public PeerIdentity {
    Objects.requireNonNull(peerId, "peerId");
    Objects.requireNonNull(sessionId, "sessionId");
  }
}
