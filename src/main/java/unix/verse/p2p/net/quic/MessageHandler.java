package unix.verse.p2p.net.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Pipeline handler for a stream whose handshake succeeded. Hands each received frame to the
 * {@link QUICPeerConnection} and closes the connection when the stream fails or ends.
 *
 * @author Damian Daniel
 * @since 0.1.0
 */
final class MessageHandler extends SimpleChannelInboundHandler<ByteBuf> {
  private static final System.Logger LOGGER = System.getLogger(MessageHandler.class.getName());

  private final QUICPeerConnection connection;

  /**
   * Creates a handler.
   *
   * @param connection receives the frames; must not be {@code null}
   */
  MessageHandler(QUICPeerConnection connection) {
    this.connection = connection;
  }

  /**
   * Copies a received frame and passes it to the connection. Runs on the stream's event loop.
   *
   * @param ctx the handler context; never {@code null}
   * @param frame the received frame, released by the caller; never {@code null}
   */
  @Override
  protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
    connection.dispatch(ByteBufUtil.getBytes(frame));
  }

  /**
   * Closes the connection because the stream ended.
   *
   * @param ctx the handler context; never {@code null}
   * @throws Exception if forwarding the event fails
   */
  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    connection.close();
    super.channelInactive(ctx);
  }

  /**
   * Logs the failure and closes the connection.
   *
   * @param ctx the handler context; never {@code null}
   * @param cause what went wrong; never {@code null}
   */
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    LOGGER.log(System.Logger.Level.WARNING, "Peer stream failed", cause);
    connection.close();
  }
}
