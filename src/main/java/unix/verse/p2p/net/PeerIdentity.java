package unix.verse.p2p.net;

import java.util.Objects;
import java.util.UUID;

public record PeerIdentity(
    UUID peerId,
    UUID sessionId,
    int protocolVersion
) {
  public PeerIdentity {
    Objects.requireNonNull(peerId, "peerId");
    Objects.requireNonNull(sessionId, "sessionId");
  }
}
