package unix.verse.p2p.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unix.verse.p2p.net.lan.LANPeerDiscovery;
import unix.verse.p2p.net.quic.QUICPeerTransport;

class PeerNetworkTest {
  private static final long TIMEOUT_SECONDS = 10;
  private static final InetSocketAddress LOOPBACK =
      new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

  private final UUID sessionId = UUID.randomUUID();
  private final List<PeerNetwork> networks = new ArrayList<>();

  @AfterEach
  void closeNetworks() {
    networks.forEach(PeerNetwork::close);
  }

  @Test
  void discoveryAutomaticallyOpensAQuicConnection() throws Exception {
    PeerNetwork host = newNetwork();
    PeerNetwork guest = newNetwork();
    BlockingQueue<PeerConnection> atHost = new LinkedBlockingQueue<>();
    BlockingQueue<PeerConnection> atGuest = new LinkedBlockingQueue<>();

    InetSocketAddress hostAddress = await(host.start(LOOPBACK, atHost::add));
    InetSocketAddress guestAddress = await(guest.start(LOOPBACK, atGuest::add));

    assertNotNull(hostAddress);
    assertNotNull(guestAddress);
    PeerConnection atHostConnection =
        atHost.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    PeerConnection atGuestConnection =
        atGuest.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(atHostConnection);
    assertNotNull(atGuestConnection);
    assertEquals(host.identity(), atGuestConnection.remoteIdentity());
    assertEquals(guest.identity(), atHostConnection.remoteIdentity());
  }

  @Test
  void startingNetworkTwiceIsRejected() throws Exception {
    PeerNetwork network = newNetwork();
    await(network.start(LOOPBACK, connection -> { }));

    assertThrows(IllegalStateException.class,
        () -> network.start(LOOPBACK, connection -> { }));
  }

  @Test
  void closedNetworkRejectsNewWork() throws Exception {
    PeerNetwork network = newNetwork();
    network.close();

    assertThrows(IllegalStateException.class,
        () -> network.start(LOOPBACK, connection -> { }));
  }

  private PeerNetwork newNetwork() {
    PeerIdentity identity = new PeerIdentity(
        UUID.randomUUID(),
        sessionId,
        Handshake.PROTOCOL_VERSION);
    QUICPeerTransport transport = new QUICPeerTransport(identity);
    PeerNetwork network = new PeerNetwork(transport, new LANPeerDiscovery());
    networks.add(network);
    return network;
  }

  private static <T> T await(java.util.concurrent.CompletableFuture<T> future)
      throws Exception {
    return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }
}
