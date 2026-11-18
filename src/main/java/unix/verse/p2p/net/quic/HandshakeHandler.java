package unix.verse.p2p.net.quic;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.ScheduledFuture;
import unix.verse.p2p.net.Handshake;
import unix.verse.p2p.net.HandshakeException;
import unix.verse.p2p.net.HandshakeException.Reason;
import unix.verse.p2p.net.PeerConnection;
import unix.verse.p2p.net.PeerIdentity;

/**
 * Pipeline handler that runs the handshake on a freshly opened stream. Both sides send their
 * identity right away. On success the handler replaces itself with a {@link MessageHandler}
 * and reports the connection. On failure or timeout it closes the whole QUIC connection and
 * reports the error, and exactly one of the two callbacks is invoked.
 *
 * @author Damian Daniel
 * @since 0.1.0
 */
final class HandshakeHandler extends SimpleChannelInboundHandler<ByteBuf> {
  private static final int LENGTH_FIELD_BYTES = 4;
  private static final int MAX_FRAME_BYTES = PeerConnection.MAX_MESSAGE_BYTES + LENGTH_FIELD_BYTES;

  private final PeerIdentity local;
  private final Duration timeout;
  private final Consumer<QUICPeerConnection> onReady;
  private final Consumer<Throwable> onFailure;
  private ScheduledFuture<?> timeoutTask;
  private boolean started;
  private boolean finished;

  private HandshakeHandler(
      PeerIdentity local,
      Duration timeout,
      Consumer<QUICPeerConnection> onReady,
      Consumer<Throwable> onFailure) {
    this.local = local;
    this.timeout = timeout;
    this.onReady = onReady;
    this.onFailure = onFailure;
  }

  /**
   * Adds length-prefixed framing and a handshake handler to a stream. The handshake starts as
   * soon as the stream is active and sends the local identity to the remote peer.
   *
   * @param stream the stream to set up; must not be {@code null}
   * @param local the identity to present; must not be {@code null}
   * @param timeout how long the remote peer has to answer; must not be {@code null}
   * @param onReady called on the stream's event loop with the new connection; must not be
   *     {@code null}
   * @param onFailure called on the stream's event loop with the cause; must not be {@code null}
   */
  static void install(
      QuicStreamChannel stream,
      PeerIdentity local,
      Duration timeout,
      Consumer<QUICPeerConnection> onReady,
      Consumer<Throwable> onFailure) {
    stream.pipeline().addLast(
        new LengthFieldBasedFrameDecoder(
            MAX_FRAME_BYTES, 0, LENGTH_FIELD_BYTES, 0, LENGTH_FIELD_BYTES),
        new LengthFieldPrepender(LENGTH_FIELD_BYTES),
        new HandshakeHandler(local, timeout, onReady, onFailure));
  }

  /**
   * Starts the handshake if the stream is already active.
   *
   * @param ctx the handler context; never {@code null}
   */
  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    if (ctx.channel().isActive())
      start(ctx);
  }

  /**
   * Starts the handshake once the stream becomes active.
   *
   * @param ctx the handler context; never {@code null}
   * @throws Exception if forwarding the event fails
   */
  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    start(ctx);
    super.channelActive(ctx);
  }

  /**
   * Decodes and validates the remote identity. Fails the handshake if it is not acceptable.
   * Frames after the first one are ignored.
   *
   * @param ctx the handler context; never {@code null}
   * @param frame the received frame, released by the caller; never {@code null}
   */
  @Override
  protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
    if (finished)
      return;
    PeerIdentity remote;
    try {
      remote = Handshake.decode(ByteBufUtil.getBytes(frame));
      Handshake.validate(local, remote);
    } catch (HandshakeException e) {
      fail(ctx, e);
      return;
    }
    finished = true;
    if (timeoutTask != null)
      timeoutTask.cancel(false);
    QUICPeerConnection connection =
        new QUICPeerConnection((QuicStreamChannel) ctx.channel(), remote);
    ctx.pipeline().replace(this, null, new MessageHandler(connection));
    onReady.accept(connection);
  }

  /**
   * Fails the handshake if the stream ended before it finished.
   *
   * @param ctx the handler context; never {@code null}
   * @throws Exception if forwarding the event fails
   */
  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    fail(ctx, new ClosedChannelException());
    super.channelInactive(ctx);
  }

  /**
   * Fails the handshake with the given error.
   *
   * @param ctx the handler context; never {@code null}
   * @param cause what went wrong; never {@code null}
   */
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    fail(ctx, cause);
  }

  private void start(ChannelHandlerContext ctx) {
    if (started)
      return;
    started = true;
    timeoutTask = ctx.executor().schedule(
        () -> fail(ctx, new HandshakeException(Reason.TIMEOUT, "Handshake timed out")),
        timeout.toMillis(),
        TimeUnit.MILLISECONDS);
    ctx.writeAndFlush(Unpooled.wrappedBuffer(Handshake.encode(local)));
  }

  private void fail(ChannelHandlerContext ctx, Throwable cause) {
    if (finished)
      return;
    finished = true;
    if (timeoutTask != null)
      timeoutTask.cancel(false);
    onFailure.accept(cause);
    ((QuicStreamChannel) ctx.channel()).parent().close();
  }
}
