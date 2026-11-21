package unix.verse.p2p.net.lan;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import unix.verse.p2p.net.DiscoveredPeer;
import unix.verse.p2p.net.PeerDiscovery;
import unix.verse.p2p.net.PeerIdentity;

/**
 * Discovers peers on the local network by repeatedly broadcasting a binary UDP announcement.
 *
 * @author Damian Daniel
 * @since 0.1.0
 */
public final class LANPeerDiscovery implements PeerDiscovery {
  /** UDP port used for LAN discovery. */
  public static final int DISCOVERY_PORT = 47841;

  private static final int MAGIC = 0x55564C41;
  private static final int ANNOUNCEMENT_SIZE = 61;
  private static final long ANNOUNCEMENT_INTERVAL_SECONDS = 1;

  private final AtomicBoolean started = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final ConcurrentMap<PeerIdentity, DiscoveredPeer> discovered = new ConcurrentHashMap<>();
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "uvmc-p2p-lan-discovery");
        thread.setDaemon(true);
        return thread;
      });
  private volatile DatagramSocket socket;
  private volatile PeerIdentity localIdentity;
  private volatile InetSocketAddress localTransportAddress;
  private volatile Consumer<DiscoveredPeer> onPeer;

  /**
   * Creates LAN discovery. Nothing is bound or advertised until {@link #start(PeerIdentity,
   * InetSocketAddress, Consumer)} is called.
   */
  public LANPeerDiscovery() {
  }

  /**
   * Starts advertising this peer and listening for peers on the local network.
   *
   * @param localIdentity identity to advertise; must not be {@code null}
   * @param transportAddress address at which this peer accepts transport connections; must not
   *     be {@code null}
   * @param onPeer called once when a peer is first discovered or its address changes; must not
   *     block and must not be {@code null}
   * @return a future that completes when discovery started, or fails with the socket bind error;
   *     never {@code null}
   * @throws NullPointerException if an argument is {@code null}
   */
  @Override
  public CompletableFuture<Void> start(
      PeerIdentity localIdentity,
      InetSocketAddress transportAddress,
      Consumer<DiscoveredPeer> onPeer) {
    Objects.requireNonNull(localIdentity, "localIdentity");
    Objects.requireNonNull(transportAddress, "transportAddress");
    Objects.requireNonNull(onPeer, "onPeer");
    CompletableFuture<Void> result = new CompletableFuture<>();

    if (closed.get()) {
      result.completeExceptionally(new IllegalStateException("Discovery is closed"));
      return result;
    }
    if (!started.compareAndSet(false, true)) {
      result.completeExceptionally(new IllegalStateException("Discovery is already started"));
      return result;
    }

    this.localIdentity = localIdentity;
    this.localTransportAddress = transportAddress;
    this.onPeer = onPeer;

    try {
      DatagramSocket bound = new DatagramSocket(null);
      bound.setReuseAddress(true);
      bound.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), DISCOVERY_PORT));
      bound.setBroadcast(true);
      socket = bound;
      startReceiver();
      executor.scheduleAtFixedRate(
          this::broadcast,
          0,
          ANNOUNCEMENT_INTERVAL_SECONDS,
          TimeUnit.SECONDS);
      result.complete(null);
    } catch (IOException e) {
      started.set(false);
      result.completeExceptionally(e);
    }
    return result;
  }

  /**
   * Stops broadcast and receiver threads and closes the discovery socket.
   */
  @Override
  public void close() {
    if (!closed.compareAndSet(false, true))
      return;
    DatagramSocket current = socket;
    if (current != null)
      current.close();
    executor.shutdownNow();
  }

  private void startReceiver() {
    Thread.ofPlatform()
        .daemon()
        .name("uvmc-p2p-lan-receiver")
        .start(() -> {
          DatagramPacket packet = new DatagramPacket(new byte[ANNOUNCEMENT_SIZE],
              ANNOUNCEMENT_SIZE);
          while (!closed.get()) {
            try {
              socket.receive(packet);
              handle(packet);
            } catch (SocketException e) {
              if (!closed.get())
                System.getLogger("unix.verse.p2p.net.lan")
                    .log(System.Logger.Level.WARNING, "LAN discovery receiver failed", e);
            } catch (IOException e) {
              if (!closed.get())
                System.getLogger("unix.verse.p2p.net.lan")
                    .log(System.Logger.Level.WARNING, "LAN discovery receive failed", e);
            }
          }
        });
  }

  private void broadcast() {
    if (closed.get())
      return;
    try {
      byte[] announcement = encode(localIdentity, localTransportAddress);
      DatagramPacket packet = new DatagramPacket(
          announcement,
          announcement.length,
          InetAddress.getByName("255.255.255.255"),
          DISCOVERY_PORT);
      socket.send(packet);
    } catch (IOException e) {
      if (!closed.get())
        System.getLogger("unix.verse.p2p.net.lan")
            .log(System.Logger.Level.WARNING, "LAN discovery broadcast failed", e);
    }
  }

  private void handle(DatagramPacket packet) {
    if (packet.getLength() != ANNOUNCEMENT_SIZE)
      return;
    try {
      DiscoveredPeer peer = decode(packet.getData());
      if (peer.identity().peerId().equals(localIdentity.peerId()))
        return;
      if (peer.identity().protocolVersion() != localIdentity.protocolVersion())
        return;
      if (!peer.identity().sessionId().equals(localIdentity.sessionId()))
        return;

      DiscoveredPeer previous = discovered.put(peer.identity(), peer);
      if (previous == null
          || !previous.transportAddress().equals(peer.transportAddress()))
        onPeer.accept(peer);
    } catch (RuntimeException e) {
      return;
    }
  }

  private static byte[] encode(PeerIdentity identity, InetSocketAddress address) {
    byte[] host = address.getAddress().getAddress();
    if (host.length != 4 && host.length != 16)
      throw new IllegalArgumentException("Discovery supports IPv4 and IPv6 addresses");

    ByteBuffer buffer = ByteBuffer.allocate(ANNOUNCEMENT_SIZE);
    buffer.putInt(MAGIC);
    putUuid(buffer, identity.peerId());
    putUuid(buffer, identity.sessionId());
    buffer.putInt(identity.protocolVersion());
    buffer.put(host.length == 4 ? (byte) 4 : (byte) 16);
    buffer.put(host);
    buffer.putInt(address.getPort());
    return buffer.array();
  }

  private static DiscoveredPeer decode(byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    if (buffer.getInt() != MAGIC)
      throw new IllegalArgumentException("Unknown discovery header");

    UUID peerId = getUuid(buffer);
    UUID sessionId = getUuid(buffer);
    int protocolVersion = buffer.getInt();
    int addressLength = Byte.toUnsignedInt(buffer.get());
    if (addressLength != 4 && addressLength != 16)
      throw new IllegalArgumentException("Invalid discovery address length");

    byte[] host = new byte[addressLength];
    buffer.get(host);
    int port = buffer.getInt();
    try {
      InetAddress address = InetAddress.getByAddress(host);
      return new DiscoveredPeer(
          new PeerIdentity(peerId, sessionId, protocolVersion),
          new InetSocketAddress(address, port));
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid discovery address", e);
    }
  }

  private static void putUuid(ByteBuffer buffer, UUID value) {
    buffer.putLong(value.getMostSignificantBits());
    buffer.putLong(value.getLeastSignificantBits());
  }

  private static UUID getUuid(ByteBuffer buffer) {
    return new UUID(buffer.getLong(), buffer.getLong());
  }
}
