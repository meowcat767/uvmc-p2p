package unix.verse.p2p.net;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface PeerConnection extends AutoCloseable {
  int MAX_MESSAGE_BYTES = 4 * 1024 * 1024;

  PeerIdentity remoteIdentity();

  InetSocketAddress remoteAddress();

  boolean isOpen();

  CompletableFuture<Void> send(byte[] message);

  void onMessage(Consumer<byte[]> listener);

  CompletableFuture<Void> closeFuture();

  @Override
  void close();
}
