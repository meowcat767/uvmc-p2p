package unix.verse.p2p.net.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

final class MessageHandler extends SimpleChannelInboundHandler<ByteBuf> {
  private static final System.Logger LOGGER = System.getLogger(MessageHandler.class.getName());

  private final QUICPeerConnection connection;

  MessageHandler(QUICPeerConnection connection) {
    this.connection = connection;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
    connection.dispatch(ByteBufUtil.getBytes(frame));
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    connection.close();
    super.channelInactive(ctx);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    LOGGER.log(System.Logger.Level.WARNING, "Peer stream failed", cause);
    connection.close();
  }
}
