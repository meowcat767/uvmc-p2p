package unix.verse.p2p.net;

import java.io.IOException;

/**
 * Signals that a handshake with a remote peer failed or was rejected.
 *
 * @author Damian Daniel
 * @since 0.1.0
 */
public final class HandshakeException extends IOException {
  /**
   * Why a handshake failed.
   */
  public enum Reason {
    /** The remote frame was not a valid handshake. */
    MALFORMED,
    /** The remote peer did not complete the handshake in time. */
    TIMEOUT,
    /** The remote peer speaks a different protocol version. */
    VERSION_MISMATCH,
    /** The remote peer belongs to a different session. */
    SESSION_MISMATCH,
    /** The remote peer has the same peer ID as the local peer. */
    SELF_CONNECTION
  }

  private final Reason reason;

  /**
   * Creates an exception.
   *
   * @param reason why the handshake failed; must not be {@code null}
   * @param message human readable detail; may be {@code null}
   */
  public HandshakeException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  /**
   * Returns why the handshake failed.
   *
   * @return the failure reason; never {@code null}
   */
  public Reason reason() {
    return reason;
  }
}
