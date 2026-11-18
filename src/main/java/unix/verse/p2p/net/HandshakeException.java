package unix.verse.p2p.net;

import java.io.IOException;

public final class HandshakeException extends IOException {
  public enum Reason {
    MALFORMED,
    TIMEOUT,
    VERSION_MISMATCH,
    SESSION_MISMATCH,
    SELF_CONNECTION
  }

  private final Reason reason;

  public HandshakeException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
