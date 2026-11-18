package unix.verse.p2p.net;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface PeerTransport extends AutoCloseable {
  PeerIdentity identity();

  CompletableFuture<InetSocketAddress> listen(
      InetSocketAddress bindAddress, Consumer<PeerConnection> onConnection);

  CompletableFuture<PeerConnection> connect(InetSocketAddress remoteAddress);

  @Override
  void close();
}
