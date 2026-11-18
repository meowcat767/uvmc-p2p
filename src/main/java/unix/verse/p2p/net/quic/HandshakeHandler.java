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

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    if (ctx.channel().isActive())
      start(ctx);
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    start(ctx);
    super.channelActive(ctx);
  }

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

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    fail(ctx, new ClosedChannelException());
    super.channelInactive(ctx);
  }

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
