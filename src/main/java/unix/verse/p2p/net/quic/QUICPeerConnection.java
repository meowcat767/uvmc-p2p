package unix.verse.p2p.net.quic;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.quic.QuicStreamChannel;
import unix.verse.p2p.net.PeerConnection;
import unix.verse.p2p.net.PeerIdentity;

/**
 * A {@link PeerConnection} carried by one bidirectional QUIC stream. Closing it closes the
 * whole QUIC connection.
 *
 * @author Damian Daniel
 * @since 0.1.0
 */
final class QUICPeerConnection implements PeerConnection {
  private final QuicStreamChannel stream;
  private final PeerIdentity remoteIdentity;
  private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();
  private final Queue<byte[]> backlog = new ArrayDeque<>();
  private Consumer<byte[]> listener;

  /**
   * Wraps a stream whose handshake succeeded.
   *
   * @param stream the stream to send and receive on; must not be {@code null}
   * @param remoteIdentity the identity the remote peer presented; must not be {@code null}
   */
  QUICPeerConnection(QuicStreamChannel stream, PeerIdentity remoteIdentity) {
    this.stream = stream;
    this.remoteIdentity = remoteIdentity;
    stream.parent().closeFuture()
        .addListener((ChannelFutureListener) future -> closeFuture.complete(null));
  }

  /**
   * Returns the identity the remote peer presented during the handshake.
   *
   * @return the remote identity; never {@code null}
   */
  @Override
  public PeerIdentity remoteIdentity() {
    return remoteIdentity;
  }

  /**
   * Returns the network address of the remote peer.
   *
   * @return the remote address; may be {@code null} once the connection is closed
   */
  @Override
  public InetSocketAddress remoteAddress() {
    return (InetSocketAddress) stream.parent().remoteSocketAddress();
  }

  /**
   * Tells whether both the stream and the QUIC connection are still active.
   *
   * @return {@code true} if messages can still be sent and received
   */
  @Override
  public boolean isOpen() {
    return stream.isActive() && stream.parent().isActive();
  }

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
  @Override
  public CompletableFuture<Void> send(byte[] message) {
    Objects.requireNonNull(message, "message");
    if (message.length > MAX_MESSAGE_BYTES) {
      return CompletableFuture.failedFuture(new IllegalArgumentException(
          "Message of " + message.length + " bytes exceeds " + MAX_MESSAGE_BYTES));
    }
    CompletableFuture<Void> result = new CompletableFuture<>();
    stream.writeAndFlush(Unpooled.wrappedBuffer(message)).addListener(
        (ChannelFutureListener) future -> {
          if (future.isSuccess())
            result.complete(null);
          else
            result.completeExceptionally(future.cause());
        });
    return result;
  }

  /**
   * Sets the listener that receives messages from the remote peer, replacing any previous
   * listener. Messages that arrived before the first listener was set are delivered to it
   * first. The listener runs on the stream's event loop and must not block.
   *
   * @param newListener receives each message; must not be {@code null}
   * @throws NullPointerException if {@code newListener} is {@code null}
   */
  @Override
  public void onMessage(Consumer<byte[]> newListener) {
    Objects.requireNonNull(newListener, "listener");
    stream.eventLoop().execute(() -> {
      listener = newListener;
      byte[] message;
      while ((message = backlog.poll()) != null)
        newListener.accept(message);
    });
  }

  /**
   * Returns a future that completes when the QUIC connection closes, whichever side closed it.
   *
   * @return the close future; never {@code null}
   */
  @Override
  public CompletableFuture<Void> closeFuture() {
    return closeFuture;
  }

  /**
   * Closes the whole QUIC connection. Does nothing if it is already closed.
   */
  @Override
  public void close() {
    stream.parent().close();
  }

  /**
   * Delivers a received message to the listener, or keeps it until a listener is set. Must be
   * called on the stream's event loop.
   *
   * @param message the received message; must not be {@code null}
   */
  void dispatch(byte[] message) {
    if (listener == null)
      backlog.add(message);
    else
      listener.accept(message);
  }
}
