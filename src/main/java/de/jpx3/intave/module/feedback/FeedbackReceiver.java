package de.jpx3.intave.module.feedback;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.diagnostic.LatencyStudy;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.module.feedback.FeedbackSender.PING_MASK;
import static de.jpx3.intave.module.feedback.FeedbackSender.TRANSACTION_MAX_CODE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.TRANSACTION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class FeedbackReceiver extends Module {
  private final static boolean USE_PING_PONG_PACKETS = MinecraftVersions.VER1_17_0.atOrAbove();
  private final static long TIMEOUT = 2000;
  private final static long TIMEOUT_KICK = TimeUnit.SECONDS.toMillis(40);
  private final static long CHECK_TIMEOUT_KICK = TIMEOUT_KICK / 4;

  public FeedbackReceiver(IntavePlugin plugin) {
    int taskId = plugin.getServer().getScheduler()
      .scheduleAsyncRepeatingTask(plugin, this::checkTransactionTimeout, CHECK_TIMEOUT_KICK, CHECK_TIMEOUT_KICK);
    TaskTracker.begun(taskId);
  }

  private void checkTransactionTimeout() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      checkTransactionTimeoutFor(player);
    }
  }

  private void checkTransactionTimeoutFor(Player player) {
    User user = userOf(player);
    ConnectionMetadata connection = user.meta().connection();
    if (oldestPendingTransaction(user) > TIMEOUT_KICK &&
      connection.eligibleForTransactionTimeout
    ) {
      IntaveLogger.logger().error(player.getName() + " is not responding to any feedback packets");
      user.synchronizedDisconnect("Timed out");
      if (IntaveControl.NETTY_DUMP_ON_TIMEOUT) {
        dumpNettyThreads();
      }
    }
  }

  private void dumpNettyThreads() {
    Thread.getAllStackTraces().forEach((thread, stackTraceElements) -> {
      if (thread.getName().contains("Netty")) {
        boolean containsIntave = false;
        for (StackTraceElement stackTraceElement : stackTraceElements) {
          if (stackTraceElement.getClassName().toLowerCase(Locale.ROOT).contains("intave")) {
            containsIntave = true;
            break;
          }
        }
        if (containsIntave) {
          System.out.println("Thread: " + thread.getName());
          Exception exception = new Exception();
          exception.setStackTrace(stackTraceElements);
          exception.printStackTrace();
        }
      }
    });
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      TRANSACTION, PONG
    }
  )
  public void receiveAcknowledgementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (user == null) {
      return;
    }
    ConnectionMetadata synchronizeData = user.meta().connection();
    Map<Long, FeedbackRequest<?>> transactionGlobalKeyMap = synchronizeData.transactionGlobalKeyMap();
    Map<Short, FeedbackRequest<?>> transactionShortKeyMap = synchronizeData.transactionShortKeyMap();
    PacketContainer packet = event.getPacket();
    short transactionIdentifier;
    if (USE_PING_PONG_PACKETS) {
      int inputInteger = packet.getIntegers().readSafely(0);
      if ((inputInteger & 0xffff0000) != PING_MASK) {
        return;
      }
      transactionIdentifier = (short) (inputInteger & 0xffff);
    } else {
      transactionIdentifier = packet.getShorts().readSafely(0);
    }
    if (transactionIdentifier <= TRANSACTION_MAX_CODE || USE_PING_PONG_PACKETS) {
      FeedbackRequest<?> transactionResponse = transactionShortKeyMap.get(transactionIdentifier);
      if (transactionResponse == null) {
        return;
      }
      long expected = synchronizeData.lastReceivedTransactionNum + 1;
      long received = transactionResponse.num();
      if (received != expected) {
        long from = Math.min(expected, received);
        long to = Math.max(expected, received);
        for (long i = from; i < to; i++) {
          FeedbackRequest<?> request = transactionGlobalKeyMap.remove(i);
          if (request == null) continue;
          transactionShortKeyMap.remove(request.key());
          receiveRequest(user, request);
        }
        user.noteHardTransactionResponse();
      }
      transactionShortKeyMap.remove(transactionIdentifier);
      transactionGlobalKeyMap.remove(transactionResponse.num());
      receiveRequest(user, transactionResponse);
      long passedTime = transactionResponse.passedTime();
      user.meta().connection().receivedTransactionAfter(passedTime);
      LatencyStudy.receivedTransactionAfter(passedTime);
      event.setCancelled(true);
    }
  }

  private void receiveRequest(User user, FeedbackRequest<?> feedbackRequest) {
    Player player = user.player();
    ConnectionMetadata synchronizeData = user.meta().connection();
    synchronizeData.lastSynchronization = feedbackRequest.requested();
    synchronizeData.lastReceivedTransactionNum = feedbackRequest.num();
    Map<Long, Queue<FeedbackRequest<?>>> appendMap = synchronizeData.transactionAppendMap();
    Queue<FeedbackRequest<?>> appendedRequests = appendMap.get(feedbackRequest.num());
    if (appendedRequests != null && !appendedRequests.isEmpty()) {
      for (FeedbackRequest<?> appendedRequest : appendedRequests) {
        appendedRequest.acknowledge(player);
      }
      appendMap.remove(feedbackRequest.num());
    }
    feedbackRequest.acknowledge(player);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void cancelAttacksIfTransactionMissing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    user.meta().connection().eligibleForTransactionTimeout = true;

    if (
      oldestPendingTransaction(user) > TIMEOUT ||
        !user.meta().connection().enqueuedPackets().isEmpty() ||
        System.currentTimeMillis() - user.meta().connection().lastEnqueue < 250
    ) {
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsIn = {
      BLOCK_DIG, BLOCK_PLACE, USE_ITEM
    }
  )
  public void cancelInteractionsOnTimeout(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    user.meta().connection().eligibleForTransactionTimeout = true;
    if (oldestPendingTransaction(user) > TIMEOUT * 2) {
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsOut = {
      SPAWN_ENTITY,
      SPAWN_ENTITY_EXPERIENCE_ORB,
      SPAWN_ENTITY_LIVING,
      NAMED_ENTITY_SPAWN,
      SPAWN_ENTITY_PAINTING,
      SPAWN_ENTITY_WEATHER,
      ENTITY_LOOK,
      ENTITY_MOVE_LOOK,
      REL_ENTITY_MOVE,
      REL_ENTITY_MOVE_LOOK,
      ENTITY_DESTROY,
      ENTITY_STATUS,
      ENTITY_METADATA,
      ENTITY_EQUIPMENT,
      ENTITY_HEAD_ROTATION,
      ENTITY_TELEPORT,
      ENTITY_VELOCITY,
      ENTITY_SOUND,
      ENTITY_EFFECT,
      REMOVE_ENTITY_EFFECT
    }
  )
  public void enqueueOutgoingPackets(PacketEvent event) {
    if (!IntaveControl.GOMME_MODE) {
      return;
    }
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ConnectionMetadata connection = user.meta().connection();
    MovementMetadata movement = user.meta().movement();

    PacketContainer packetContainer = event.getPacket();
    PacketType packetType = event.getPacketType();
    Deque<Object> enqueuedPackets = connection.enqueuedPackets();

    if (user.justJoined()) {
      return;
    }

    if (connection.ignorePacket) {
      connection.ignorePacket = false;
      return;
    }

//    if (connection.packets++ % 100 == 0) {
//      Synchronizer.synchronize(() -> {
//        player.setLevel((int) connection.transactionPingAverage());
//      });
//    }

    long playerLatencyGain = connection.transactionPingAverage() - LatencyStudy.transactionPingAverage();
    boolean significantPingGain = playerLatencyGain > 100; // trustfactor?

    long lastMovementPacket = System.currentTimeMillis() - connection.lastMovementPacket();
    long oldestTransactionPacket = oldestPendingTransaction(user);
    long positionTimeoutTolerance = user.meta().protocol().flyingPacketStream() ? 0 : 1050;

    boolean transactionTimeout = oldestTransactionPacket > connection.transactionPingAverage() + LatencyStudy.transactionPingAverage() / 2 + 300;
    boolean riding = movement.isInVehicle();
    boolean positionTimeout = !riding && lastMovementPacket > connection.transactionPingAverage() + LatencyStudy.transactionPingAverage() / 2 + 300 + positionTimeoutTolerance;

    boolean idAddressed =
      packetType == PacketType.Play.Server.ENTITY_STATUS ||
      packetType == PacketType.Play.Server.ENTITY_METADATA ||
      packetType == PacketType.Play.Server.ENTITY_VELOCITY
      ;

    if (idAddressed) {
      Integer entityId = packetContainer.getIntegers().read(0);
      if (entityId != null && entityId == player.getEntityId()) {
        return;
      }
    }

    boolean tooManyPackets = enqueuedPackets.size() > 4000;
    boolean buffer = !tooManyPackets && (transactionTimeout || positionTimeout);
//    boolean enqueueLater = significantPingGain

    if (buffer) {
      enqueuedPackets.offerLast(packetContainer.getHandle());
      connection.lastEnqueue = System.currentTimeMillis();
      event.setCancelled(true);
    } else if (!enqueuedPackets.isEmpty()) {
      if (enqueuedPackets.size() > 100) {
        // send up to 100 packets in the queue by poll
        for (int i = 0; i < 10; i++) {
          Object packet = enqueuedPackets.pollFirst();
          if (packet == null) break;
          connection.ignorePacket = true;
          sendPacket(player, packet);
        }
        enqueuedPackets.offerLast(packetContainer.getHandle());
        event.setCancelled(true);
      } else {
        // send all packets in the queue by poll
        while (!enqueuedPackets.isEmpty()) {
          Object packet = enqueuedPackets.pollFirst();
          connection.ignorePacket = true;
          sendPacket(player, packet);
        }
      }
      connection.lastEnqueue = System.currentTimeMillis();
    }
  }

  private void sendPacket(Player player, Object packet) {
    try {
      ProtocolLibrary.getProtocolManager().sendServerPacket(player, PacketContainer.fromPacket(packet), true);
    } catch (InvocationTargetException exception) {
      exception.printStackTrace();
    }
  }

  public long oldestPendingTransaction(User user) {
    ConnectionMetadata synchronizeData = user.meta().connection();
    Map<Short, FeedbackRequest<?>> transactionFeedBackMap = synchronizeData.transactionShortKeyMap();
    long duration = System.currentTimeMillis();
    for (FeedbackRequest<?> value : transactionFeedBackMap.values()) {
      duration = Math.min(duration, value.requested());
    }
    return System.currentTimeMillis() - duration;
  }

  public User userOf(Player player) {
    return UserRepository.userOf(player);
  }
}
