package unix.verse.p2p.net;

import java.nio.ByteBuffer;
import java.util.UUID;

import unix.verse.p2p.net.HandshakeException.Reason;

/**
 * Wire format and validation rules for the handshake two peers exchange when they connect.
 *
 * <p>A handshake frame is {@link #ENCODED_SIZE} bytes: a magic header, the protocol version,
 * the session ID and the peer ID, in that order.
 *
 * @author Damian Daniel
 * @since 0.1.0
 */
public final class Handshake {
  /** Protocol version this build speaks. */
  public static final int PROTOCOL_VERSION = 1;

  /** Size in bytes of an encoded handshake frame. */
  public static final int ENCODED_SIZE = 40;

  private static final int MAGIC = 0x55565032;

  private Handshake() {
  }

  /**
   * Encodes an identity into a handshake frame.
   *
   * @param identity the identity to encode; must not be {@code null}
   * @return a new array of {@link #ENCODED_SIZE} bytes; never {@code null}
   * @throws NullPointerException if {@code identity} is {@code null}
   */
  public static byte[] encode(PeerIdentity identity) {
    ByteBuffer buffer = ByteBuffer.allocate(ENCODED_SIZE);
    buffer.putInt(MAGIC);
    buffer.putInt(identity.protocolVersion());
    putUuid(buffer, identity.sessionId());
    putUuid(buffer, identity.peerId());
    return buffer.array();
  }

  /**
   * Decodes a handshake frame. Does not check that the identity is acceptable, see
   * {@link #validate(PeerIdentity, PeerIdentity)}.
   *
   * @param frame the frame to decode; must not be {@code null}
   * @return the decoded identity; never {@code null}
   * @throws HandshakeException with {@link Reason#MALFORMED} if the frame has the wrong length
   *     or header
   * @throws NullPointerException if {@code frame} is {@code null}
   */
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

  /**
   * Checks that a remote peer may talk to the local peer. The protocol version is checked
   * first, then the session, then whether the remote peer is the local peer.
   *
   * @param local the identity of this peer; must not be {@code null}
   * @param remote the identity the other peer presented; must not be {@code null}
   * @throws HandshakeException with {@link Reason#VERSION_MISMATCH},
   *     {@link Reason#SESSION_MISMATCH} or {@link Reason#SELF_CONNECTION} if the remote peer
   *     is not acceptable
   * @throws NullPointerException if {@code local} or {@code remote} is {@code null}
   */
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
