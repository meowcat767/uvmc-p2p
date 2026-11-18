package unix.verse.p2p.net;

import java.nio.ByteBuffer;
import java.util.UUID;

import unix.verse.p2p.net.HandshakeException.Reason;

public final class Handshake {
  public static final int PROTOCOL_VERSION = 1;
  public static final int ENCODED_SIZE = 40;

  private static final int MAGIC = 0x55565032;

  private Handshake() {
  }

  public static byte[] encode(PeerIdentity identity) {
    ByteBuffer buffer = ByteBuffer.allocate(ENCODED_SIZE);
    buffer.putInt(MAGIC);
    buffer.putInt(identity.protocolVersion());
    putUuid(buffer, identity.sessionId());
    putUuid(buffer, identity.peerId());
    return buffer.array();
  }

  public static PeerIdentity decode(byte[] frame) throws HandshakeException {
    if (frame.length != ENCODED_SIZE) {
      throw new HandshakeException(Reason.MALFORMED,
          "Expected " + ENCODED_SIZE + " handshake bytes but got " + frame.length);
    }
    ByteBuffer buffer = ByteBuffer.wrap(frame);
    if (buffer.getInt() != MAGIC)
      throw new HandshakeException(Reason.MALFORMED, "Unrecognized handshake header");
    int protocolVersion = buffer.getInt();
    UUID sessionId = getUuid(buffer);
    UUID peerId = getUuid(buffer);
    return new PeerIdentity(peerId, sessionId, protocolVersion);
  }

  public static void validate(PeerIdentity local, PeerIdentity remote) throws HandshakeException {
    if (remote.protocolVersion() != local.protocolVersion()) {
      throw new HandshakeException(Reason.VERSION_MISMATCH,
          "Peer speaks protocol " + remote.protocolVersion()
              + " but this peer speaks " + local.protocolVersion());
    }
    if (!remote.sessionId().equals(local.sessionId()))
      throw new HandshakeException(Reason.SESSION_MISMATCH, "Peer belongs to another session");
    if (remote.peerId().equals(local.peerId()))
      throw new HandshakeException(Reason.SELF_CONNECTION, "Peer has the same ID as this peer");
  }

  private static void putUuid(ByteBuffer buffer, UUID value) {
    buffer.putLong(value.getMostSignificantBits());
    buffer.putLong(value.getLeastSignificantBits());
  }

  private static UUID getUuid(ByteBuffer buffer) {
    long mostSignificant = buffer.getLong();
    long leastSignificant = buffer.getLong();
    return new UUID(mostSignificant, leastSignificant);
  }
}
