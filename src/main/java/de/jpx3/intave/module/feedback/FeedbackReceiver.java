package de.jpx3.intave.module.feedback;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.module.feedback.FeedbackSender.PING_MASK;
import static de.jpx3.intave.module.feedback.FeedbackSender.TRANSACTION_MAX_CODE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class FeedbackReceiver extends Module {
  private final static boolean USE_PING_PONG_PACKETS = MinecraftVersions.VER1_17_0.atOrAbove();
  private final static long TIMEOUT = 2000;
  private final static long TIMEOUT_KICK = TimeUnit.SECONDS.toMillis(40);
  private final static long CHECK_TIMEOUT_KICK = TIMEOUT_KICK / 4;

  public FeedbackReceiver(IntavePlugin plugin) {
    //    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
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
  public void onPacketReceiving(PacketEvent event) {
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
    if (oldestPendingTransaction(user) > TIMEOUT) {
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsIn = {
      BLOCK_DIG, BLOCK_PLACE, USE_ITEM
    }
  )
  public void on(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    user.meta().connection().eligibleForTransactionTimeout = true;
    if (oldestPendingTransaction(user) > TIMEOUT * 2) {
      event.setCancelled(true);
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
