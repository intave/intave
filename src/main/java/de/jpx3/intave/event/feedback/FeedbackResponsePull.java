package de.jpx3.intave.event.feedback;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Queue;

import static de.jpx3.intave.event.feedback.FeedbackService.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class FeedbackResponsePull implements PacketEventSubscriber {
  private final IntavePlugin plugin;

  public FeedbackResponsePull(IntavePlugin plugin) {
    this.plugin = plugin;
    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
    plugin.getServer().getScheduler().scheduleAsyncRepeatingTask(plugin, this::checkTransactionTimeout, 20 * 2, 20 * 2);
  }

  private void checkTransactionTimeout() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      checkTransactionTimeoutFor(player);
    }
  }

  private void checkTransactionTimeoutFor(Player player) {
    User user = userOf(player);
    if (oldestPendingTransaction(user) > TRANSACTION_TIMEOUT_KICK) {
      IntaveLogger.logger().error(player.getName() + " is not responding to any feedback packets");
      user.synchronizedDisconnect("Timed out");
    }
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
    Map<Long, Request<?>> transactionGlobalKeyMap = synchronizeData.transactionGlobalKeyMap();
    Map<Short, Request<?>> transactionShortKeyMap = synchronizeData.transactionShortKeyMap();
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
      Request<?> transactionResponse = transactionShortKeyMap.get(transactionIdentifier);
      if (transactionResponse == null) {
        return;
      }
      long expected = synchronizeData.lastReceivedTransactionNum + 1;
      long received = transactionResponse.num();
      if (received != expected) {
        long from = Math.min(expected, received);
        long to = Math.max(expected, received);
        for (long i = from; i < to; i++) {
          Request<?> request = transactionGlobalKeyMap.remove(i);
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

  private void receiveRequest(User user, Request<?> transactionResponse) {
    Player player = user.player();
    ConnectionMetadata synchronizeData = user.meta().connection();
    synchronizeData.lastSynchronization = transactionResponse.requested();
    synchronizeData.lastReceivedTransactionNum = transactionResponse.num();
    Map<Long, Queue<Request<?>>> appendixMap = synchronizeData.transactionAppendixMap();
    Queue<Request<?>> appendixRequests = appendixMap.get(transactionResponse.num());
    if (appendixRequests != null && !appendixRequests.isEmpty()) {
      for (Request<?> appendixRequest : appendixRequests) {
        appendixRequest.acknowledge(player);
      }
      appendixMap.remove(transactionResponse.num());
    }
    transactionResponse.acknowledge(player);
  }

  private <T> T convertInstanceOfObject(Object o) {
    try {
      //noinspection unchecked
      return (T) o;
    } catch (ClassCastException e) {
      return null;
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
    if (oldestPendingTransaction(user) > TRANSACTION_TIMEOUT) {
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
    if (oldestPendingTransaction(user) > TRANSACTION_TIMEOUT * 2) {
      event.setCancelled(true);
    }
  }

  private static long oldestPendingTransaction(User user) {
    ConnectionMetadata synchronizeData = user.meta().connection();
    Map<Short, Request<?>> transactionFeedBackMap = synchronizeData.transactionShortKeyMap();
    long duration = AccessHelper.now();
    for (Request<?> value : transactionFeedBackMap.values()) {
      duration = Math.min(duration, value.requested());
    }
    return AccessHelper.now() - duration;
  }

  public User userOf(Player player) {
    return UserRepository.userOf(player);
  }
}
