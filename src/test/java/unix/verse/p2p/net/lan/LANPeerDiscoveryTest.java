package unix.verse.p2p.net.lan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unix.verse.p2p.net.DiscoveredPeer;
import unix.verse.p2p.net.Handshake;
import unix.verse.p2p.net.PeerDiscovery;
import unix.verse.p2p.net.PeerIdentity;

class LANPeerDiscoveryTest {
  private static final Duration START_TIMEOUT = Duration.ofSeconds(2);
  private static final long DISCOVERY_TIMEOUT_SECONDS = 5;
  private static final InetSocketAddress LOOPBACK =
      new InetSocketAddress(InetAddress.getLoopbackAddress(), 4433);

  private final UUID sessionId = UUID.randomUUID();
  private final java.util.List<PeerDiscovery> discoveries = new java.util.ArrayList<>();

  @AfterEach
  void closeDiscoveries() {
    discoveries.forEach(PeerDiscovery::close);
  }

  @Test
  void peersDiscoverEachOtherInTheSameSession() throws Exception {
    PeerIdentity host = identity(sessionId, Handshake.PROTOCOL_VERSION);
    PeerIdentity guest = identity(sessionId, Handshake.PROTOCOL_VERSION);
    LANPeerDiscovery hostDiscovery = newDiscovery();
    LANPeerDiscovery guestDiscovery = newDiscovery();
    BlockingQueue<DiscoveredPeer> atHost = new LinkedBlockingQueue<>();
    BlockingQueue<DiscoveredPeer> atGuest = new LinkedBlockingQueue<>();

    start(hostDiscovery, host, LOOPBACK, atHost::add);
    start(guestDiscovery, guest,
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 4434),
        atGuest::add);

    DiscoveredPeer fromHost = atHost.poll(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    DiscoveredPeer fromGuest = atGuest.poll(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertNotNull(fromHost);
    assertNotNull(fromGuest);
    assertEquals(guest, fromHost.identity());
    assertEquals(4434, fromHost.transportAddress().getPort());
    assertEquals(host, fromGuest.identity());
    assertEquals(4433, fromGuest.transportAddress().getPort());
  }

  @Test
  void discoveryFiltersOtherSessions() throws Exception {
    PeerIdentity host = identity(sessionId, Handshake.PROTOCOL_VERSION);
    PeerIdentity stranger = identity(UUID.randomUUID(), Handshake.PROTOCOL_VERSION);
    LANPeerDiscovery hostDiscovery = newDiscovery();
    LANPeerDiscovery strangerDiscovery = newDiscovery();
    BlockingQueue<DiscoveredPeer> discovered = new LinkedBlockingQueue<>();

    start(hostDiscovery, host, LOOPBACK, discovered::add);
    start(strangerDiscovery, stranger, LOOPBACK, discovered::add);

    Thread.sleep(1500);
    assertNull(discovered.poll());
  }

  @Test
  void discoveryFiltersDifferentProtocolVersions() throws Exception {
    PeerIdentity host = identity(sessionId, Handshake.PROTOCOL_VERSION);
    PeerIdentity outdated = identity(sessionId, Handshake.PROTOCOL_VERSION + 1);
    LANPeerDiscovery hostDiscovery = newDiscovery();
    LANPeerDiscovery outdatedDiscovery = newDiscovery();
    BlockingQueue<DiscoveredPeer> discovered = new LinkedBlockingQueue<>();

    start(hostDiscovery, host, LOOPBACK, discovered::add);
    start(outdatedDiscovery, outdated, LOOPBACK, discovered::add);

    Thread.sleep(1500);
    assertNull(discovered.poll());
  }

  @Test
  void startingTwiceIsRejected() throws Exception {
    LANPeerDiscovery discovery = newDiscovery();
    PeerIdentity identity = identity(sessionId, Handshake.PROTOCOL_VERSION);
    start(discovery, identity, LOOPBACK, peer -> { });

    ExecutionException failure = assertThrows(ExecutionException.class,
        () -> discovery.start(identity, LOOPBACK, peer -> { }).get());

    assertInstanceOf(IllegalStateException.class, failure.getCause());
  }

  @Test
  void closedDiscoveryRefusesNewWork() throws Exception {
    LANPeerDiscovery discovery = newDiscovery();
    discovery.close();

    ExecutionException failure = assertThrows(ExecutionException.class,
        () -> discovery.start(
            identity(sessionId, Handshake.PROTOCOL_VERSION),
            LOOPBACK,
            peer -> { }).get());

    assertInstanceOf(IllegalStateException.class, failure.getCause());
  }

  private LANPeerDiscovery newDiscovery() {
    LANPeerDiscovery discovery = new LANPeerDiscovery();
    discoveries.add(discovery);
    return discovery;
  }

  private static PeerIdentity identity(UUID session, int protocolVersion) {
    return new PeerIdentity(UUID.randomUUID(), session, protocolVersion);
  }

  private static void start(
      LANPeerDiscovery discovery,
      PeerIdentity identity,
      InetSocketAddress address,
      java.util.function.Consumer<DiscoveredPeer> listener) throws Exception {
    discovery.start(identity, address, listener).get(
        START_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
  }
}
