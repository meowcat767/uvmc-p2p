package unix.verse.p2p.net.quic;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import unix.verse.p2p.net.PeerConnection;
import unix.verse.p2p.net.PeerIdentity;
import unix.verse.p2p.net.PeerTransport;

public final class QUICPeerTransport implements PeerTransport {
  private static final System.Logger LOGGER =
      System.getLogger(QUICPeerTransport.class.getName());

  private static final String ALPN = "uvp2p";
  private static final int IO_THREADS = 2;
  private static final Duration DEFAULT_HANDSHAKE_TIMEOUT = Duration.ofSeconds(5);
  private static final long IDLE_TIMEOUT_MILLIS = 30_000;
  private static final long CONNECTION_WINDOW_BYTES = 16L * 1024 * 1024;
  private static final long STREAM_WINDOW_BYTES = 4L * 1024 * 1024;

  private static final ChannelHandler REJECT_STREAMS = new ChannelInitializer<QuicStreamChannel>() {
    @Override
    protected void initChannel(QuicStreamChannel stream) {
      stream.close();
    }
  };

  private final PeerIdentity identity;
  private final Duration handshakeTimeout;
  private final EventLoopGroup group;
  private final QuicSslContext serverContext;
  private final QuicSslContext clientContext;
  private final List<Channel> channels = new CopyOnWriteArrayList<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private CompletableFuture<Channel> clientChannel;

  public QUICPeerTransport(PeerIdentity identity) {
    this(identity, DEFAULT_HANDSHAKE_TIMEOUT);
  }

  public QUICPeerTransport(PeerIdentity identity, Duration handshakeTimeout) {
    this.identity = Objects.requireNonNull(identity, "identity");
    this.handshakeTimeout = Objects.requireNonNull(handshakeTimeout, "handshakeTimeout");
    PeerCertificate certificate;
    try {
      certificate = PeerCertificate.generate("uvp2p-" + identity.peerId());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to generate the peer certificate", e);
    }
    this.serverContext = QuicSslContextBuilder
        .forServer(certificate.privateKey(), null, certificate.certificate())
        .applicationProtocols(ALPN)
        .build();
    this.clientContext = QuicSslContextBuilder.forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .applicationProtocols(ALPN)
        .build();
    this.group = new MultiThreadIoEventLoopGroup(
        IO_THREADS, new DefaultThreadFactory("uvp2p-quic", true), NioIoHandler.newFactory());
  }

  @Override
  public PeerIdentity identity() {
    return identity;
  }

  @Override
  public CompletableFuture<InetSocketAddress> listen(
      InetSocketAddress bindAddress, Consumer<PeerConnection> onConnection) {
    Objects.requireNonNull(bindAddress, "bindAddress");
    Objects.requireNonNull(onConnection, "onConnection");
    CompletableFuture<InetSocketAddress> result = new CompletableFuture<>();
    if (closed.get()) {
      result.completeExceptionally(new IllegalStateException("Transport is closed"));
      return result;
    }

    ChannelHandler codec = new QuicServerCodecBuilder()
        .sslContext(serverContext)
        .maxIdleTimeout(IDLE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .initialMaxData(CONNECTION_WINDOW_BYTES)
        .initialMaxStreamDataBidirectionalLocal(STREAM_WINDOW_BYTES)
        .initialMaxStreamDataBidirectionalRemote(STREAM_WINDOW_BYTES)
        .initialMaxStreamsBidirectional(1)
        .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
          @Override
          protected void initChannel(QuicStreamChannel stream) {
            HandshakeHandler.install(
                stream,
                identity,
                handshakeTimeout,
                connection -> deliver(onConnection, connection),
                cause -> LOGGER.log(
                    System.Logger.Level.INFO, "Rejected inbound peer: {0}", cause.toString()));
          }
        })
        .build();

    ChannelFuture bound = new Bootstrap()
        .group(group)
        .channel(NioDatagramChannel.class)
        .handler(codec)
        .bind(bindAddress);
    bound.addListener((ChannelFutureListener) future -> {
      if (future.isSuccess()) {
        channels.add(future.channel());
        result.complete((InetSocketAddress) future.channel().localAddress());
      } else {
        result.completeExceptionally(future.cause());
      }
    });
    return result;
  }

  @Override
  public CompletableFuture<PeerConnection> connect(InetSocketAddress remoteAddress) {
    Objects.requireNonNull(remoteAddress, "remoteAddress");
    CompletableFuture<PeerConnection> result = new CompletableFuture<>();
    if (closed.get()) {
      result.completeExceptionally(new IllegalStateException("Transport is closed"));
      return result;
    }

    clientChannel().whenComplete((datagramChannel, error) -> {
      if (error != null) {
        result.completeExceptionally(error);
        return;
      }
      QuicChannel.newBootstrap(datagramChannel)
          .handler(new ChannelInboundHandlerAdapter())
          .streamHandler(REJECT_STREAMS)
          .remoteAddress(remoteAddress)
          .connect()
          .addListener((Future<QuicChannel> connected) -> {
            if (connected.isSuccess())
              openControlStream(connected.getNow(), result);
            else
              result.completeExceptionally(connected.cause());
          });
    });
    return result;
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true))
      return;
    for (Channel channel : channels)
      channel.close();
    group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
  }

  private void openControlStream(
      QuicChannel quicChannel, CompletableFuture<PeerConnection> result) {
    ChannelHandler initializer = new ChannelInitializer<QuicStreamChannel>() {
      @Override
      protected void initChannel(QuicStreamChannel stream) {
        HandshakeHandler.install(
            stream,
            identity,
            handshakeTimeout,
            result::complete,
            result::completeExceptionally);
      }
    };
    quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, initializer)
        .addListener((Future<QuicStreamChannel> created) -> {
          if (!created.isSuccess()) {
            result.completeExceptionally(created.cause());
            quicChannel.close();
          }
        });
  }

  private synchronized CompletableFuture<Channel> clientChannel() {
    if (clientChannel == null) {
      CompletableFuture<Channel> pending = new CompletableFuture<>();
      clientChannel = pending;
      new Bootstrap()
          .group(group)
          .channel(NioDatagramChannel.class)
          .handler(clientCodec())
          .bind(new InetSocketAddress(0))
          .addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
              channels.add(future.channel());
              pending.complete(future.channel());
            } else {
              pending.completeExceptionally(future.cause());
            }
          });
    }
    return clientChannel;
  }

  private ChannelHandler clientCodec() {
    return new QuicClientCodecBuilder()
        .sslContext(clientContext)
        .maxIdleTimeout(IDLE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .initialMaxData(CONNECTION_WINDOW_BYTES)
        .initialMaxStreamDataBidirectionalLocal(STREAM_WINDOW_BYTES)
        .initialMaxStreamDataBidirectionalRemote(STREAM_WINDOW_BYTES)
        .build();
  }

  private static void deliver(Consumer<PeerConnection> onConnection, PeerConnection connection) {
    try {
      onConnection.accept(connection);
    } catch (RuntimeException e) {
      LOGGER.log(System.Logger.Level.WARNING, "Connection listener failed", e);
      connection.close();
    }
  }
}
