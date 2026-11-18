package unix.verse.p2p.net.quic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unix.verse.p2p.net.Handshake;
import unix.verse.p2p.net.PeerConnection;
import unix.verse.p2p.net.PeerIdentity;
import unix.verse.p2p.net.PeerTransport;

class QUICPeerTransportTest {
  private static final long TIMEOUT_SECONDS = 10;
  private static final int BURST_SIZE = 100;
  private static final InetSocketAddress LOOPBACK =
      new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

  private final UUID sessionId = UUID.randomUUID();
  private final List<PeerTransport> transports = new ArrayList<>();

  @AfterEach
  void closeTransports() {
    transports.forEach(PeerTransport::close);
  }

  @Test
  void peersExchangeIdentitiesOnConnect() throws Exception {
    QUICPeerTransport host = newTransport();
    QUICPeerTransport guest = newTransport();
    CompletableFuture<PeerConnection> inbound = new CompletableFuture<>();
    InetSocketAddress address = listen(host, inbound::complete);

    PeerConnection outbound = await(guest.connect(address));

    assertEquals(host.identity(), outbound.remoteIdentity());
    assertEquals(guest.identity(), await(inbound).remoteIdentity());
    assertTrue(outbound.isOpen());
  }

  @Test
  void messagesFlowInBothDirections() throws Exception {
    QUICPeerTransport host = newTransport();
    QUICPeerTransport guest = newTransport();
    CompletableFuture<PeerConnection> inbound = new CompletableFuture<>();
    InetSocketAddress address = listen(host, inbound::complete);
    PeerConnection outbound = await(guest.connect(address));
    PeerConnection accepted = await(inbound);
    CompletableFuture<byte[]> atHost = new CompletableFuture<>();
    CompletableFuture<byte[]> atGuest = new CompletableFuture<>();
    accepted.onMessage(atHost::complete);
    outbound.onMessage(atGuest::complete);

    await(outbound.send(bytes("ping")));
    assertArrayEquals(bytes("ping"), await(atHost));
    await(accepted.send(bytes("pong")));
    assertArrayEquals(bytes("pong"), await(atGuest));
  }

  @Test
  void messagesArriveInOrder() throws Exception {
    QUICPeerTransport host = newTransport();
    QUICPeerTransport guest = newTransport();
    CompletableFuture<PeerConnection> inbound = new CompletableFuture<>();
    InetSocketAddress address = listen(host, inbound::complete);
    PeerConnection outbound = await(guest.connect(address));
    BlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
    await(inbound).onMessage(received::add);

    for (int i = 0; i < BURST_SIZE; i++)
      outbound.send(ByteBuffer.allocate(Integer.BYTES).putInt(i).array());

    for (int i = 0; i < BURST_SIZE; i++) {
      byte[] message = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      assertNotNull(message);
      assertEquals(i, ByteBuffer.wrap(message).getInt());
    }
  }

  @Test
  void largeMessageSurvivesRoundTrip() throws Exception {
    QUICPeerTransport host = newTransport();
    QUICPeerTransport guest = newTransport();
    CompletableFuture<PeerConnection> inbound = new CompletableFuture<>();
    InetSocketAddress address = listen(host, inbound::complete);
    PeerConnection outbound = await(guest.connect(address));
    CompletableFuture<byte[]> atHost = new CompletableFuture<>();
    await(inbound).onMessage(atHost::complete);
    byte[] payload = new byte[1024 * 1024];
    new Random(7).nextBytes(payload);

    await(outbound.send(payload));

    assertArrayEquals(payload, await(atHost));
  }

  @Test
  void messageSentBeforeListenerRegistrationIsDelivered() throws Exception {
    QUICPeerTransport host = newTransport();
    QUICPeerTransport guest = newTransport();
    CompletableFuture<PeerConnection> inbound = new CompletableFuture<>();
    InetSocketAddress address = listen(host, inbound::complete);
    PeerConnection outbound = await(guest.connect(address));
    PeerConnection accepted = await(inbound);

    await(outbound.send(bytes("early")));
    Thread.sleep(200);
    CompletableFuture<byte[]> atHost = new CompletableFuture<>();
    accepted.onMessage(atHost::complete);

    assertArrayEquals(bytes("early"), await(atHost));
  }

  @Test
  void connectionFailsWhenSessionsDiffer() throws Exception {
    QUICPeerTransport host = newTransport();
    QUICPeerTransport stranger = newTransport(UUID.randomUUID(), Handshake.PROTOCOL_VERSION);
    CompletableFuture<PeerConnection> inbound = new CompletableFuture<>();
    InetSocketAddress address = listen(host, inbound::complete);

    assertRejected(stranger.connect(address), inbound);
  }

  @Test
  void connectionFailsWhenProtocolVersionsDiffer() throws Exception {
    QUICPeerTransport host = newTransport();
    QUICPeerTransport outdated = newTransport(sessionId, Handshake.PROTOCOL_VERSION + 1);
    CompletableFuture<PeerConnection> inbound = new CompletableFuture<>();
    InetSocketAddress address = listen(host, inbound::complete);

    assertRejected(outdated.connect(address), inbound);
  }

  @Test
  void connectionFailsWhenConnectingToSelf() throws Exception {
    QUICPeerTransport host = newTransport();
    CompletableFuture<PeerConnection> inbound = new CompletableFuture<>();
    InetSocketAddress address = listen(host, inbound::complete);

    assertRejected(host.connect(address), inbound);
  }

  @Test
  void closingOneSideClosesTheOther() throws Exception {
    QUICPeerTransport host = newTransport();
    QUICPeerTransport guest = newTransport();
    CompletableFuture<PeerConnection> inbound = new CompletableFuture<>();
    InetSocketAddress address = listen(host, inbound::complete);
    PeerConnection outbound = await(guest.connect(address));
    PeerConnection accepted = await(inbound);

    outbound.close();

    await(accepted.closeFuture());
    assertFalse(accepted.isOpen());
    assertFalse(outbound.isOpen());
  }

  @Test
  void sendRejectsOversizedMessages() throws Exception {
    QUICPeerTransport host = newTransport();
    QUICPeerTransport guest = newTransport();
    InetSocketAddress address = listen(host, connection -> { });
    PeerConnection outbound = await(guest.connect(address));

    ExecutionException failure = assertThrows(ExecutionException.class,
        () -> await(outbound.send(new byte[PeerConnection.MAX_MESSAGE_BYTES + 1])));

    assertInstanceOf(IllegalArgumentException.class, failure.getCause());
  }

  @Test
  void closedTransportRefusesNewWork() throws Exception {
    QUICPeerTransport transport = newTransport();
    transport.close();

    ExecutionException listenFailure = assertThrows(ExecutionException.class,
        () -> await(transport.listen(LOOPBACK, connection -> { })));
    ExecutionException connectFailure = assertThrows(ExecutionException.class,
        () -> await(transport.connect(LOOPBACK)));

    assertInstanceOf(IllegalStateException.class, listenFailure.getCause());
    assertInstanceOf(IllegalStateException.class, connectFailure.getCause());
  }

  private QUICPeerTransport newTransport() {
    return newTransport(sessionId, Handshake.PROTOCOL_VERSION);
  }

  private QUICPeerTransport newTransport(UUID session, int protocolVersion) {
    PeerIdentity identity = new PeerIdentity(UUID.randomUUID(), session, protocolVersion);
    QUICPeerTransport transport = new QUICPeerTransport(identity, Duration.ofSeconds(3));
    transports.add(transport);
    return transport;
  }

  private static InetSocketAddress listen(
      PeerTransport transport, Consumer<PeerConnection> onConnection) throws Exception {
    return await(transport.listen(LOOPBACK, onConnection));
  }

  private static <T> T await(CompletableFuture<T> future) throws Exception {
    return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  private static void assertRejected(
      CompletableFuture<PeerConnection> attempt, CompletableFuture<PeerConnection> inbound) {
    ExecutionException failure = assertThrows(ExecutionException.class, () -> await(attempt));

    assertInstanceOf(IOException.class, failure.getCause());
    assertThrows(TimeoutException.class, () -> inbound.get(300, TimeUnit.MILLISECONDS));
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }
}
