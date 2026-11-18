package unix.verse.p2p.net;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import unix.verse.p2p.net.HandshakeException.Reason;

class HandshakeTest {
  private static final UUID SESSION = UUID.randomUUID();

  private final PeerIdentity local =
      new PeerIdentity(UUID.randomUUID(), SESSION, Handshake.PROTOCOL_VERSION);

  @Test
  void encodedIdentityRoundTrips() throws HandshakeException {
    byte[] encoded = Handshake.encode(local);

    assertEquals(Handshake.ENCODED_SIZE, encoded.length);
    assertEquals(local, Handshake.decode(encoded));
  }

  @Test
  void decodeRejectsWrongLength() {
    HandshakeException truncated = assertThrows(HandshakeException.class,
        () -> Handshake.decode(new byte[Handshake.ENCODED_SIZE - 1]));
    HandshakeException oversized = assertThrows(HandshakeException.class,
        () -> Handshake.decode(new byte[Handshake.ENCODED_SIZE + 1]));

    assertEquals(Reason.MALFORMED, truncated.reason());
    assertEquals(Reason.MALFORMED, oversized.reason());
  }

  @Test
  void decodeRejectsUnknownHeader() {
    byte[] encoded = Handshake.encode(local);
    encoded[0] ^= 0xFF;

    HandshakeException failure =
        assertThrows(HandshakeException.class, () -> Handshake.decode(encoded));

    assertEquals(Reason.MALFORMED, failure.reason());
  }

  @Test
  void validateAcceptsAnotherPeerOfTheSameSession() {
    PeerIdentity remote =
        new PeerIdentity(UUID.randomUUID(), SESSION, Handshake.PROTOCOL_VERSION);

    assertDoesNotThrow(() -> Handshake.validate(local, remote));
  }

  @Test
  void validateRejectsDifferentProtocolVersion() {
    PeerIdentity remote =
        new PeerIdentity(UUID.randomUUID(), SESSION, Handshake.PROTOCOL_VERSION + 1);

    HandshakeException failure =
        assertThrows(HandshakeException.class, () -> Handshake.validate(local, remote));

    assertEquals(Reason.VERSION_MISMATCH, failure.reason());
  }

  @Test
  void validateRejectsDifferentSession() {
    PeerIdentity remote =
        new PeerIdentity(UUID.randomUUID(), UUID.randomUUID(), Handshake.PROTOCOL_VERSION);

    HandshakeException failure =
        assertThrows(HandshakeException.class, () -> Handshake.validate(local, remote));

    assertEquals(Reason.SESSION_MISMATCH, failure.reason());
  }

  @Test
  void validateRejectsConnectionToSelf() {
    HandshakeException failure =
        assertThrows(HandshakeException.class, () -> Handshake.validate(local, local));

    assertEquals(Reason.SELF_CONNECTION, failure.reason());
  }

  @Test
  void validateReportsVersionBeforeSession() {
    PeerIdentity remote =
        new PeerIdentity(UUID.randomUUID(), UUID.randomUUID(), Handshake.PROTOCOL_VERSION + 1);

    HandshakeException failure =
        assertThrows(HandshakeException.class, () -> Handshake.validate(local, remote));

    assertEquals(Reason.VERSION_MISMATCH, failure.reason());
  }

  @Test
  void identityRequiresPeerIdAndSessionId() {
    assertThrows(NullPointerException.class,
        () -> new PeerIdentity(null, SESSION, Handshake.PROTOCOL_VERSION));
    assertThrows(NullPointerException.class,
        () -> new PeerIdentity(UUID.randomUUID(), null, Handshake.PROTOCOL_VERSION));
  }
}
