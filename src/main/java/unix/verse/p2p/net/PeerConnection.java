package unix.verse.p2p.net;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * An established connection to a remote peer whose handshake already succeeded. Messages are
 * delivered reliably and in order.
 *
 * @author Damian Daniel
 * @since 0.1.0
 */
public interface PeerConnection extends AutoCloseable {
  /** Largest message in bytes that {@link #send(byte[])} accepts. */
  int MAX_MESSAGE_BYTES = 4 * 1024 * 1024;

  /**
   * Returns the identity the remote peer presented during the handshake.
   *
   * @return the remote identity; never {@code null}
   */
  PeerIdentity remoteIdentity();

  /**
   * Returns the network address of the remote peer.
   *
   * @return the remote address; may be {@code null} once the connection is closed
   */
  InetSocketAddress remoteAddress();

  /**
   * Tells whether messages can still be sent and received.
   *
   * @return {@code true} if the connection is open
   */
  boolean isOpen();

  /**
   * Sends a message to the remote peer. The write happens asynchronously.
   *
   * @param message the message to send; must not be {@code null} and must not be longer than
   *     {@link #MAX_MESSAGE_BYTES}
   * @return a future that completes once the message was written, or fails with
   *     {@link IllegalArgumentException} if the message is too long or with the transport
   *     error if the write failed; never {@code null}
   * @throws NullPointerException if {@code message} is {@code null}
   */
  CompletableFuture<Void> send(byte[] message);

  /**
   * Sets the listener that receives messages from the remote peer, replacing any previous
   * listener. Messages that arrived before the first listener was set are delivered to it
   * first. The listener runs on a transport thread and must not block.
   *
   * @param listener receives each message; must not be {@code null}
   * @throws NullPointerException if {@code listener} is {@code null}
   */
  void onMessage(Consumer<byte[]> listener);

  /**
   * Returns a future that completes when the connection closes, whichever side closed it.
   *
   * @return the close future; never {@code null}
   */
  CompletableFuture<Void> closeFuture();

  /**
   * Closes the connection. Does nothing if it is already closed.
   */
  @Override
  void close();
}
