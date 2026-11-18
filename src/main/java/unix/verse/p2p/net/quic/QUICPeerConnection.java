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

final class QUICPeerConnection implements PeerConnection {
  private final QuicStreamChannel stream;
  private final PeerIdentity remoteIdentity;
  private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();
  private final Queue<byte[]> backlog = new ArrayDeque<>();
  private Consumer<byte[]> listener;

  QUICPeerConnection(QuicStreamChannel stream, PeerIdentity remoteIdentity) {
    this.stream = stream;
    this.remoteIdentity = remoteIdentity;
    stream.parent().closeFuture()
        .addListener((ChannelFutureListener) future -> closeFuture.complete(null));
  }

  @Override
  public PeerIdentity remoteIdentity() {
    return remoteIdentity;
  }

  @Override
  public InetSocketAddress remoteAddress() {
    return (InetSocketAddress) stream.parent().remoteSocketAddress();
  }

  @Override
  public boolean isOpen() {
    return stream.isActive() && stream.parent().isActive();
  }

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

  @Override
  public CompletableFuture<Void> closeFuture() {
    return closeFuture;
  }

  @Override
  public void close() {
    stream.parent().close();
  }

  void dispatch(byte[] message) {
    if (listener == null)
      backlog.add(message);
    else
      listener.accept(message);
  }
}
