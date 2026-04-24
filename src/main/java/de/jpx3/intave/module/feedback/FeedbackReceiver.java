package de.jpx3.intave.module.feedback;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.diagnostic.LatencyStudy;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.module.feedback.FeedbackSender.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class FeedbackReceiver extends Module {
  private static final boolean USE_PING_PACKETS = MinecraftVersions.VER1_17_0.atOrAbove();
  private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(8);
  private static final long TIMEOUT_KICK = TimeUnit.SECONDS.toMillis(40);
  private static final long CHECK_TIMEOUT_KICK = TIMEOUT_KICK / 4;

  public FeedbackReceiver(IntavePlugin plugin) {
    int taskId = plugin.getServer().getScheduler()
      .scheduleAsyncRepeatingTask(plugin, this::checkTransactionTimeout, CHECK_TIMEOUT_KICK, CHECK_TIMEOUT_KICK);
    TaskTracker.begun(taskId);

    int taskId2 = plugin.getServer().getScheduler()
      .scheduleAsyncRepeatingTask(plugin, this::decreaseTAKAVL, 20 * 60, 20 * 60);
    TaskTracker.begun(taskId2);
  }

  private void decreaseTAKAVL() {
    UserRepository.applyOnAll(user -> {
      user.meta().connection().transactionKeepAliveInvalidOrderVL = Math.max(0, user.meta().connection().transactionKeepAliveInvalidOrderVL - 1);
    });
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
      connection.eligibleForTransactionTimeout &&
      FaultKicks.IGNORING_FEEDBACK
    ) {
      IntaveLogger.logger().error(player.getName() + " is not responding to any feedback packets");
      user.kick("Not responding to feedback packets");
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
    packetsIn = WINDOW_CLICK
  )
  public void receiveInventoryClick(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
//    ConnectionMetadata connection = user.meta().connection();
//    connection.windowClickId++;
//    connection.windowClickId %= 250;
//    int start = Short.MAX_VALUE - 250;
//    packet.getShorts().writeSafely(0, (short) (connection.windowClickId + start));
  }

  @PacketSubscription(
    packetsIn = {
      KEEP_ALIVE
    }
  )
  public void onKeepAlive(ProtocolPacketEvent event) {
    if (!IntaveControl.CLIENT_KEEP_ALIVE_NETTY_CHECK) {
      return;
    }
    Player player = event.getPlayer();
    User user = userOf(player);
    FeedbackQueue feedbackQueue = user.meta().connection().feedbackQueue();
    short possibleUserKey = (short) new WrapperPlayClientKeepAlive((PacketReceiveEvent) event).getId();
    FeedbackRequest<?> peek = feedbackQueue.peek(possibleUserKey);
    if (peek != null) {
      peek.verifyPreThreadInjection();
//      System.out.println("Verified " + possibleUserKey + " for " + player.getName());
    }
  }

  @PacketSubscription(
    packetsOut = {
      PacketId.Server.TRANSACTION, PacketId.Server.PING
    }
  )
  public void outgoingTransaction(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    boolean noPingMask = user.meta().protocol().noPingMask();
    FeedbackKey key = outgoingKey((PacketSendEvent) event);
    if (!hasValidUserKey(key, noPingMask) && activeGenerator != IdGeneratorMode.highestCompatibility()) {
      short userKey = userKeyFrom(key);
      boolean couldBeWindowClick = userKey >= Short.MAX_VALUE - 250;
      if (couldBeWindowClick) {
        return;
      }
      activeGenerator = IdGeneratorMode.highestCompatibility();
      IntaveLogger.logger().info("Detected foreign transaction id " + userKey + " for " + player.getName());
      IntaveLogger.logger().info("Switching to highest compatibility transaction id selection mode");
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      TRANSACTION, PONG
    }
  )
  public void receiveAcknowledgementPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();

    // viaversion packet limit workaround
    ViaVersionAdapter.decrementReceivedPackets(player, 2);

    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }
    MetadataBundle meta = user.meta();
    ConnectionMetadata connection = meta.connection();
    FeedbackQueue feedbackQueue = connection.feedbackQueue();
    boolean noPingMask = user.meta().protocol().noPingMask();
    FeedbackKey key = incomingKey((PacketReceiveEvent) event);

    if (!hasValidUserKey(key, noPingMask)) {
      if (IntaveControl.DEBUG_FEEDBACK_PACKETS) {
        System.out.println("Received " + key.id + " from " + player.getName() + " but no user key was found");
      }
      return;
    }

    short userKey = userKeyFrom(key);
    FeedbackRequest<?> response = feedbackQueue.peek(userKey);
    if (response == null) {
      if (IntaveControl.DEBUG_FEEDBACK_PACKETS) {
        System.out.println("Received " + userKey + "/" + key.id + " from " + player.getName() + " but no request was found");
      }
      return;
    }

    long expected = connection.lastReceivedTransactionNum + 1;
    long received = response.num();

    if (IntaveControl.DEBUG_FEEDBACK_PACKETS) {
      System.out.println("Expected: " + expected + ", received: " + received);
    }

    if (received != expected) {
      for (FeedbackRequest<?> missedRequest : feedbackQueue.pollUpTo(Math.max(expected, received))) {
        if (IntaveControl.DEBUG_FEEDBACK_PACKETS) {
          System.out.println("Emulating " + missedRequest.userKey() + "/" + missedRequest.num() + " for " + player.getName());
        }
        receiveRequest(user, missedRequest);
      }
      user.noteFeedbackFault();
    }

    if (IntaveControl.DEBUG_FEEDBACK_PACKETS) {
      System.out.println("Received " + userKey + "/" + response.num() + " from " + player.getName());
//      Synchronizer.synchronize(() -> {
//        player.sendMessage("Received " + userKey + "/" +response.num());
//      });
    }

    FeedbackRequest<?> poll = feedbackQueue.poll();
    if (poll != response) {
      throw new IllegalStateException("Polling from feedback queue did not return the expected request");
    }

    if (IntaveControl.CLIENT_KEEP_ALIVE_NETTY_CHECK) {
      if (!response.preThreadInjectionPassed() && !MinecraftVersions.VER1_12_0.atOrAbove() && !user.meta().protocol().affectedByLevitation()) {
        if (connection.transactionKeepAliveInvalidOrderVL++ > 10) {
//          Violation violation = Violation.builderFor(ProtocolScanner.class)
//            .forPlayer(user.player())
//            .withMessage("invalid transaction/keepalive order")
//            .withDetails("player version: " + user.meta().protocol().versionString())
//            .withVL(1)
//            .build();
//          Modules.violationProcessor().processViolation(violation);
          if (connection.transactionKeepAliveInvalidOrderVL > 20) {
            connection.transactionKeepAliveInvalidOrderVL = 10;
          }
        }
      }
    }

    receiveRequest(user, response);
    long passedTime = response.passedTime();
    connection.receivedTransactionAfter(passedTime);
    Modules.feedbackAnalysis().receivedTransaction(user, response);

//    Balance.BalanceMeta balanceMeta = (Balance.BalanceMeta) user.checkMetadata(Balance.BalanceMeta.class);
//
//    if (balanceMeta.confirmedBalance != Integer.MAX_VALUE) {
//      balanceMeta.timerBalance = Math.max(balanceMeta.timerBalance, balanceMeta.confirmedBalance);
//      balanceMeta.confirmedBalance = Integer.MAX_VALUE;
//    }

//    long nextConfirmedBalance = -passedTimeNs;
//    connection.nextFeedbackSubscribers.add(() -> {
//      balanceMeta.nextConfirmedBalance = nextConfirmedBalance - TimeUnit.MILLISECONDS.toNanos(1);
//    });

    LatencyStudy.receivedTransactionAfter(passedTime);
    event.setCancelled(true);
  }

  private FeedbackKey incomingKey(PacketReceiveEvent event) {
    if (event.getPacketType() == PacketType.Play.Client.PONG) {
      return new FeedbackKey(new WrapperPlayClientPong(event).getId(), true);
    }
    return new FeedbackKey(new WrapperPlayClientWindowConfirmation(event).getActionId(), false);
  }

  private FeedbackKey outgoingKey(PacketSendEvent event) {
    if (event.getPacketType() == PacketType.Play.Server.PING) {
      return new FeedbackKey(new WrapperPlayServerPing(event).getId(), true);
    }
    return new FeedbackKey(new WrapperPlayServerWindowConfirmation(event).getActionId(), false);
  }

  private short userKeyFrom(FeedbackKey key) {
    if (key.ping) {
      return (short) (key.id & 0xffff);
    }
    return (short) key.id;
  }

  private boolean hasValidUserKey(FeedbackKey key, boolean noPingMask) {
    short shortInput;
    if (key.ping) {
      if ((key.id & 0xffff0000) != PING_MASK && !noPingMask) {
        return false;
      }
      shortInput = (short) (key.id & 0xffff);
    } else {
      shortInput = (short) key.id;
    }
    return shortInput <= MAX_USER_KEY && shortInput >= MIN_USER_KEY;
  }

  private static final class FeedbackKey {
    private final int id;
    private final boolean ping;

    private FeedbackKey(int id, boolean ping) {
      this.id = id;
      this.ping = ping;
    }
  }

  private void receiveRequest(User user, FeedbackRequest<?> feedbackRequest) {
    Player player = user.player();
    ConnectionMetadata connection = user.meta().connection();
    for (Runnable nextFeedbackSubscriber : connection.nextFeedbackSubscribers) {
      nextFeedbackSubscriber.run();
    }
    connection.nextFeedbackSubscribers.clear();
    connection.lastSynchronization = feedbackRequest.requestedAsNanos();
    connection.lastReceivedTransactionNum = feedbackRequest.num();
    Map<Long, Queue<FeedbackRequest<?>>> appendMap = connection.transactionAppendMap();
    Queue<FeedbackRequest<?>> appendedRequests = appendMap.get(feedbackRequest.num());
    if (appendedRequests != null && !appendedRequests.isEmpty()) {
      for (FeedbackRequest<?> appendedRequest : appendedRequests) {
        acknowledge(player, appendedRequest);
      }
      appendMap.remove(feedbackRequest.num());
    }
    acknowledge(player, feedbackRequest);
  }

  private void acknowledge(Player player, FeedbackRequest<?> feedbackRequest) {
    try {
      feedbackRequest.acknowledge(player);
    } catch (Exception e) {
      if (IntaveControl.DISABLE_LICENSE_CHECK) {
        IntaveLogger.logger().error("Error while acknowledging " + feedbackRequest.callback() + " for " + feedbackRequest.target());
        e.printStackTrace();
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void cancelAttacksIfTransactionMissing(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ConnectionMetadata connection = user.meta().connection();
    connection.eligibleForTransactionTimeout = true;
    if (oldestPendingTransaction(user) > TIMEOUT ||
      // Logically, this is part of the PacketDelayer,
      // but I've put this stuff in this if-clause to have a common place for
      // any transaction-related attack cancels
      !connection.enqueuedPackets().isEmpty() ||
      System.currentTimeMillis() - connection.timestampRequiredForAttack < 0
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
  public void cancelInteractionsOnTimeout(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    user.meta().connection().eligibleForTransactionTimeout = true;
    if (oldestPendingTransaction(user) > TIMEOUT * 2) {
      event.setCancelled(true);
    }
  }

  public long oldestPendingTransaction(User user) {
    ConnectionMetadata connection = user.meta().connection();
    FeedbackRequest<?> peek = connection.feedbackQueue().peek();
    return peek == null ? 0 : peek.passedTime();
  }

  public User userOf(Player player) {
    return UserRepository.userOf(player);
  }
}
