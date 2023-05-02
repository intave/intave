package de.jpx3.intave.module.feedback;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.movement.timer.Balance;
import de.jpx3.intave.diagnostic.LatencyStudy;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.module.feedback.FeedbackSender.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class FeedbackReceiver extends Module {
  private static final boolean USE_PING_PONG_PACKETS = MinecraftVersions.VER1_17_0.atOrAbove();
  private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(2);
  private static final long TIMEOUT_KICK = TimeUnit.SECONDS.toMillis(40);
  private static final long CHECK_TIMEOUT_KICK = TIMEOUT_KICK / 4;

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
  public void receiveInventoryClick(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketContainer packet = event.getPacket();
    Short clientTransactionId = packet.getShorts().readSafely(0);
    if (clientTransactionId == null) {
      return;
    }
    ConnectionMetadata connection = user.meta().connection();
    connection.windowClickId++;
    connection.windowClickId %= 1000;
    int start = Short.MAX_VALUE - 1000;
    packet.getShorts().writeSafely(0, (short) (connection.windowClickId + start));
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
    if (!user.hasPlayer()) {
      return;
    }
    MetadataBundle meta = user.meta();
    ProtocolMetadata protocol = meta.protocol();
    ConnectionMetadata connection = meta.connection();
    Map<Long, FeedbackRequest<?>> transactionGlobalKeyMap = connection.transactionGlobalKeyMap();
    Map<Short, FeedbackRequest<?>> transactionShortKeyMap = connection.transactionShortKeyMap();
    Queue<FeedbackRequest<?>> feedbackRequests = connection.pendingFeedbackRequests();
    PacketContainer packet = event.getPacket();
    short transactionIdentifier = identifierFrom(packet, protocol.noPingMask());
    if (transactionIdentifier == -1) {
      return;
    }
    FeedbackRequest<?> transactionResponse = transactionShortKeyMap.remove(transactionIdentifier);
    if (transactionResponse == null) {
      return;
    }
    if (IntaveControl.DEBUG_FEEDBACK_PACKETS) {
      System.out.println("Received " + transactionIdentifier + "/" +transactionResponse.num() + " from " + player.getName());
    }
    long expected = connection.lastReceivedTransactionNum + 1;
    long received = transactionResponse.num();
    if (received != expected) {
      long from = Math.min(expected, received);
      long to = Math.max(expected, received);
      for (long i = from; i < to; i++) {
        FeedbackRequest<?> request = transactionGlobalKeyMap.remove(i);
        if (request == null) continue;
        FeedbackRequest<?> localRequest = transactionShortKeyMap.remove(request.userKey());
        if (request != localRequest) {
          // This should never happen
          throw new IllegalStateException("Transaction key mismatch alpha");
        }
        FeedbackRequest<?> expectedRequest = feedbackRequests.poll();
        if (request != expectedRequest) {
          // This should never happen
          throw new IllegalStateException("Transaction key mismatch beta");
        }
        if (IntaveControl.DEBUG_FEEDBACK_PACKETS) {
          System.out.println("Emulating " + localRequest.userKey() + "/" +localRequest.num() + " for " + player.getName());
        }
        receiveRequest(user, request);
      }
      user.noteFeedbackFault();
    }

    FeedbackRequest<?> poll = feedbackRequests.poll();
    if (poll != transactionResponse) {
      throw new IllegalStateException("Polling from feedback queue did not return the expected request");
    }

//    transactionShortKeyMap.remove(transactionIdentifier);
    transactionGlobalKeyMap.remove(transactionResponse.num());
    receiveRequest(user, transactionResponse);
    long passedTime = transactionResponse.passedTime();
    connection.receivedTransactionAfter(passedTime);

    // to be changed (!)
    Balance.BalanceMeta balanceMeta = (Balance.BalanceMeta) user.checkMetadata(Balance.BalanceMeta.class);
    balanceMeta.timerBalance = Math.max(balanceMeta.timerBalance, balanceMeta.confirmedBalance);
    balanceMeta.nextConfirmedBalance = -passedTime;

    connection.pendingTransactions--;
    LatencyStudy.receivedTransactionAfter(passedTime);
    event.setCancelled(true);
  }

  private short identifierFrom(PacketContainer packet, boolean noPingMask) {
    if (USE_PING_PONG_PACKETS) {
      int inputInteger = packet.getIntegers().readSafely(0);
      if (noPingMask) {
        if (inputInteger >= 0) {
          return -1;
        }
        inputInteger = -inputInteger;
      } else {
        if ((inputInteger & 0xffff0000) != PING_MASK) {
          return -1;
        }
      }
      return  (short) (inputInteger & 0xffff);
    } else {
      short shortInput = packet.getShorts().readSafely(0);
      if (shortInput > TRANSACTION_MAX_CODE || shortInput < TRANSACTION_MIN_CODE) {
        return -1;
      }
      return shortInput;
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
        castSafeAcknowledgement(player, appendedRequest);
      }
      appendMap.remove(feedbackRequest.num());
    }
    castSafeAcknowledgement(player, feedbackRequest);
  }

  private void castSafeAcknowledgement(Player player, FeedbackRequest<?> feedbackRequest) {
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
  public void cancelAttacksIfTransactionMissing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ConnectionMetadata connection = user.meta().connection();
    connection.eligibleForTransactionTimeout = true;
    if (oldestPendingTransaction(user) > TIMEOUT ||
      // Logically, this is part of the PacketDelayer,
      // but I've put this stuff in this if-clause to have a common place for
      // any transaction-related attack cancels
      !connection.enqueuedPackets().isEmpty() ||
      System.currentTimeMillis() - connection.lastBufferEnqueue < 750
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

  public long oldestPendingTransaction(User user) {
    ConnectionMetadata connection = user.meta().connection();
//    Map<Short, FeedbackRequest<?>> transactionFeedBackMap = connection.transactionShortKeyMap();
//    long duration = System.currentTimeMillis();
//    for (FeedbackRequest<?> value : transactionFeedBackMap.values()) {
//      duration = Math.min(duration, value.requested());
//    }
    Queue<FeedbackRequest<?>> feedbackRequests = connection.pendingFeedbackRequests();
    FeedbackRequest<?> peek = feedbackRequests.peek();
    if (peek == null) {
      return 0;
    }
    return System.currentTimeMillis() - peek.requested();
  }

  public User userOf(Player player) {
    return UserRepository.userOf(player);
  }
}
