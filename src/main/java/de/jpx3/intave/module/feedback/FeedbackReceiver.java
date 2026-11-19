/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.feedback;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.diagnostic.LatencyStudy;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packetperfect.OnReceive;
import de.jpx3.intave.packet.contract.immutable.PongPacket;
import de.jpx3.intave.packet.contract.immutable.TransactionPacket;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.module.feedback.FeedbackSender.*;
import static de.jpx3.intave.packet.contract.PacketType.Client.*;

public final class FeedbackReceiver extends Module {
  private static final boolean USE_PING_PACKETS = MinecraftVersions.VER1_17_0.atOrAbove();
  private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(8);
  private static final long TIMEOUT_KICK = TimeUnit.SECONDS.toMillis(40);
  private static final long CHECK_TIMEOUT_KICK_TICKS = 20 * 10;

  public FeedbackReceiver() {
    Tasks.periodicNamed(
      "FeedbackReceiver.transactionTimeout",
      this::checkTransactionTimeout,
      CHECK_TIMEOUT_KICK_TICKS,
      CHECK_TIMEOUT_KICK_TICKS
    ).startAsync();
  }

  private void checkTransactionTimeout() {
    UserRepository.applyOnOnlineUsers(user ->
      Synchronizer.synchronize(user, () -> checkTransactionTimeoutFor(user))
    );
  }

  private void checkTransactionTimeoutFor(User user) {
    ConnectionMetadata connection = user.meta().connection();
    if (oldestPendingTransaction(user) > TIMEOUT_KICK &&
      connection.eligibleForTransactionTimeout &&
      FaultKicks.IGNORING_FEEDBACK
    ) {
      IntaveLogger.logger().error(user.player().getName() + " is not responding to any feedback packets");
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
    priority = ListenerPriority.LOWEST,
    packetsIn = {
	    TRANSACTION_IN, PONG
    }
  )
  public void receiveAcknowledgementPacket(PacketEvent event) {
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
    PacketContainer packet = event.getPacket();
    boolean noPingMask = user.meta().protocol().noPingMask();

    if (!hasValidUserKey(packet, noPingMask)) {
      if (IntaveControl.DEBUG_FEEDBACK_PACKETS) {
        System.out.println("Received " + packet.getIntegers().readSafely(0) + " from " + player.getName() + " but no user key was found");
      }
      return;
    }

    short userKey = userKeyFrom(packet);
    FeedbackRequest<?> response = feedbackQueue.peek(userKey);
    if (response == null) {
      if (IntaveControl.DEBUG_FEEDBACK_PACKETS) {
        System.out.println("Received " + userKey + "/" + packet.getIntegers().readSafely(0) + " from " + player.getName() + " but no request was found");
      }
      return;
    }

    receiveAcknowledgement(user, response, event);
  }

  @OnReceive(priority = de.jpx3.intave.packet.ListenerPriority.FIRST)
  public void receivePong(User user, PongPacket packet, Cancellable cancellable) {
    if ((packet.id() & 0xffff0000) == PING_MASK) {
      receiveAcknowledgement(user, (short) packet.id(), cancellable);
    }
  }

  @OnReceive(priority = de.jpx3.intave.packet.ListenerPriority.FIRST)
  public void receiveTransaction(User user, TransactionPacket packet, Cancellable cancellable) {
    if (packet.containerId() == 0) {
      receiveAcknowledgement(user, packet.actionNumber(), cancellable);
    }
  }

  private void receiveAcknowledgement(User user, short key, Cancellable cancellable) {
    FeedbackRequest<?> response = user.meta().connection().feedbackQueue().peek(key);
    if (IntaveControl.DEBUG_FEEDBACK_PACKETS) {
      System.out.println("Received " + key + " from " + user.player().getName() + " and found " + (response == null ? "no request" : response.userKey() + "/" + response.num()));
    }
    if (response != null && response.nativeTransport()) {
      receiveAcknowledgement(user, response, cancellable);
    }
  }

  private void receiveAcknowledgement(User user, FeedbackRequest<?> response, Cancellable event) {
    Player player = user.player();
    ConnectionMetadata connection = user.meta().connection();
    FeedbackQueue feedbackQueue = connection.feedbackQueue();
    short userKey = response.userKey();

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

  private short userKeyFrom(PacketContainer packet) {
    if (USE_PING_PACKETS) {
      int inputInteger = packet.getIntegers().readSafely(0);
      return (short) (inputInteger & 0xffff);
    } else {
      return packet.getShorts().readSafely(0);
    }
  }

  private boolean hasValidUserKey(PacketContainer packet, boolean noPingMask) {
    short shortInput;
    if (USE_PING_PACKETS) {
      int inputInteger = packet.getIntegers().readSafely(0);
      if ((inputInteger & 0xffff0000) != PING_MASK && !noPingMask) {
        return false;
      }
      shortInput = (short) (inputInteger & 0xffff);
    } else {
      shortInput = packet.getShorts().readSafely(0);
    }
    return shortInput <= MAX_USER_KEY && shortInput >= MIN_USER_KEY;
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
      if (IntaveControl.DEBUG) {
        IntaveLogger.logger().error("Error while acknowledging " + feedbackRequest.callback() + " for " + feedbackRequest.target());
        e.printStackTrace();
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
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
    FeedbackRequest<?> peek = connection.feedbackQueue().peek();
    return peek == null ? 0 : peek.passedTime();
  }

  public User userOf(Player player) {
    return UserRepository.userOf(player);
  }
}
