package de.jpx3.intave.event.transaction;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketEventSubscriber;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaConnectionData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Queue;

import static de.jpx3.intave.event.packet.PacketId.Client.*;
import static de.jpx3.intave.event.transaction.TransactionFeedbackService.*;

public final class TransactionResponseEnforcingProcessor implements PacketEventSubscriber {
  private final IntavePlugin plugin;

  public TransactionResponseEnforcingProcessor(IntavePlugin plugin) {
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
      IntaveLogger.logger().pushPrintln("[Intave] " + player.getName() + " is not responding to validation packets");
      user.synchronizedDisconnect("Timed out");
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      TRANSACTION
    }
  )
  public void onPacketReceiving(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (user == null) {
      return;
    }
    UserMetaConnectionData synchronizeData = user.meta().connectionData();
    Map<Long, TFRequest<?>> transactionGlobalKeyMap = synchronizeData.transactionGlobalKeyMap();
    Map<Short, TFRequest<?>> transactionShortKeyMap = synchronizeData.transactionShortKeyMap();
    Short transactionIdentifier = event.getPacket().getShorts().readSafely(0);
    if (transactionIdentifier <= TRANSACTION_MAX_CODE) {
      try {
        synchronizeData.transactionLock.lock();
        TFRequest<?> transactionResponse = transactionShortKeyMap.remove(transactionIdentifier);
        if (transactionResponse == null) {
          return;
        }
        transactionGlobalKeyMap.remove(transactionResponse.num());
        long expected = synchronizeData.lastReceivedTransactionNum + 1;
        long received = transactionResponse.num();
        if (received != expected) {
          IntaveLogger.logger().pushPrintln("[Intave] " + player.getName() + " sent invalid validation response (received " + received + ", but expected " + expected + ")");
          long from = Math.min(expected, received);
          long to = Math.max(expected, received);
          for (long i = from; i < to; i++) {
            TFRequest<?> request = transactionGlobalKeyMap.remove(i);
            if (request == null) continue;
            transactionShortKeyMap.remove(request.key());
            receiveRequest(user, request);
          }
        } else {
          receiveRequest(user, transactionResponse);
        }
        event.setCancelled(true);
      } finally {
        synchronizeData.transactionLock.unlock();
      }
    }
  }

  private void receiveRequest(User user, TFRequest<?> transactionResponse) {
    Player player = user.player();
    UserMetaConnectionData synchronizeData = user.meta().connectionData();
    synchronizeData.lastSynchronization = transactionResponse.requested();
    synchronizeData.lastReceivedTransactionNum = transactionResponse.num();
    transactionResponse.callback().success(player, convertInstanceOfObject(transactionResponse.lock()));
    Map<Long, Queue<TFRequest<?>>> appendixMap = synchronizeData.transactionAppendixMap();
    Queue<TFRequest<?>> appendixRequests = appendixMap.get(transactionResponse.num());
    if (appendixRequests != null && !appendixRequests.isEmpty()) {
      for (TFRequest<?> appendixRequest : appendixRequests) {
        appendixRequest.callback().success(player, convertInstanceOfObject(appendixRequest.lock()));
      }
      appendixMap.remove(transactionResponse.num());
    }
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
    UserMetaConnectionData synchronizeData = user.meta().connectionData();
    Map<Short, TFRequest<?>> transactionFeedBackMap = synchronizeData.transactionShortKeyMap();
    long duration = AccessHelper.now();
    for (TFRequest<?> value : transactionFeedBackMap.values()) {
      duration = Math.min(duration, value.requested());
    }
    return AccessHelper.now() - duration;
  }

  public User userOf(Player player) {
    return UserRepository.userOf(player);
  }
}
