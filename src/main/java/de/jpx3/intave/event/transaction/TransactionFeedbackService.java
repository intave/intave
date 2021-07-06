package de.jpx3.intave.event.transaction;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.event.packet.PacketEventSubscriber;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaConnectionData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

import static de.jpx3.intave.event.transaction.TransactionFeedbackService.TransactionOptions.*;

public final class TransactionFeedbackService implements PacketEventSubscriber {
  public final static long TRANSACTION_TIMEOUT = 3000;
  public final static long TRANSACTION_TIMEOUT_KICK = 20000;
  public final static short TRANSACTION_MIN_CODE = -32768;
  public final static short TRANSACTION_MAX_CODE = -16370;
  public final static long OPTIONAL_PENDING_LIMIT = 20;
  public final static long OPTIONAL_SENT_LIMIT = 100;

  private final static ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
  private final TransactionResponseEnforcingProcessor responseLocker;

  public TransactionFeedbackService(IntavePlugin plugin) {
    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
    responseLocker = new TransactionResponseEnforcingProcessor(plugin);
  }

  public <T> void doubleSynchronize(
    Player player, PacketEvent event, T target,
    TFCallback<T> firstCallback, TFCallback<T> secondCallback
  ) {
    doubleSynchronize(player, event, target, firstCallback, secondCallback, 0);
  }

  public <T> void doubleSynchronize(
    Player player, PacketEvent event, T target,
    TFCallback<T> firstCallback, TFCallback<T> secondCallback,
    int options
  ) {
    doubleSynchronize(player, event.getPacket(), target, firstCallback, secondCallback, options);
    event.setCancelled(true);
  }

  public <T> void doubleSynchronize(
    Player player, PacketContainer encapsulate,
    T target, TFCallback<T> firstCallback, TFCallback<T> secondCallback
  ) {
    doubleSynchronize(player, encapsulate, target, firstCallback, secondCallback, 0);
  }

  public <T> void doubleSynchronize(
    Player player,
    PacketContainer encapsulate, T target,
    TFCallback<T> firstCallback, TFCallback<T> secondCallback,
    int options
  ) {
    User user = UserRepository.userOf(player);
    if (user == null || !user.hasOnlinePlayer()) {
      return;
    }
    singleSynchronize(player, target, firstCallback, options);
    user.ignoreNextOutboundPacket();
    sendPacket(player, encapsulate);
    singleSynchronize(player, target, secondCallback, options);
  }

  public <T> void singleSynchronize(
    Player player, T target, TFCallback<T> callback
  ) {
    singleSynchronize(player, target, callback, 0);
  }

  public <T> void singleSynchronize(
    Player player, T target, TFCallback<T> callback, int options
  ) {
    if (!Bukkit.isPrimaryThread()) {
      if (TransactionOptions.matches(SELF_SYNCHRONIZATION, options)) {
        Synchronizer.synchronize(() -> singleSynchronize(player, target, callback, options));
      } else {
        IntaveLogger.logger().error("Can't perform tick-validation off main thread");
        IntaveLogger.logger().error("Please check if you sent a packet / performed a bukkit player action asynchronously in the following trace:");
        Thread.dumpStack();
        callback.success(player, target);
      }
      return;
    }
    User user = UserRepository.userOf(player);
    if (user == null || !user.hasOnlinePlayer()) {
      return;
    }
    boolean append = false;
    if (TransactionOptions.matches(APPEND_ON_OVERFLOW, options)) {
      boolean tooManyPending = pendingTransactions(userOf(player)) > OPTIONAL_PENDING_LIMIT;
      boolean sentTooManyRecently = user.meta().connectionData().transactionPacketCounter > OPTIONAL_SENT_LIMIT;
      append = tooManyPending || sentTooManyRecently;
    }
    if (TransactionOptions.matches(APPEND, options)) {
      append = pendingTransactions(userOf(player)) > 0;
    }
    if (append) {
      appendRequestToContext(player, target, callback);
      return;
    }
    countTransactionPacket(player);
    sendTransactionPacket(player, acquireNewId(player, target, callback));
  }

  private final static Object FALLBACK_OBJECT = new Object();

  private <T> void appendRequestToContext(
    Player player, T obj, TFCallback<T> callback
  ) {
    User user = UserRepository.userOf(player);
    if (user == null || !user.hasOnlinePlayer()) {
      return;
    }
    UserMetaConnectionData synchronizeData = user.meta().connectionData();
    Queue<TFRequest<?>> queue = synchronizeData
      .transactionAppendixMap()
      .computeIfAbsent(synchronizeData.transactionNumCounter, aLong -> new LinkedBlockingDeque<>());
    if (obj == null) {
      //noinspection unchecked
      obj = (T) FALLBACK_OBJECT;
    }
    queue.add(new TFRequest<>(callback, obj, (short) -1, -1));
  }

  private /* synchronized (is already always sync) */ <T> short acquireNewId(
    Player player, T obj, TFCallback<T> callback
  ) {
    User user = UserRepository.userOf(player);
    UserMetaConnectionData synchronizeData = user.meta().connectionData();
    short transactionKey = findAvailableTransactionIdFor(player);
    if (transactionKey >= TRANSACTION_MAX_CODE) {
      synchronizeData.transactionCounter = TRANSACTION_MIN_CODE;
    }
    long transactionNumCounter = synchronizeData.transactionNumCounter++;
    if (obj == null) {
      //noinspection unchecked
      obj = (T) FALLBACK_OBJECT;
    }
    TFRequest<T> feedbackEntry = new TFRequest<>(callback, obj, transactionKey, transactionNumCounter);
    synchronizeData.transactionShortKeyMap().put(transactionKey, feedbackEntry);
    synchronizeData.transactionGlobalKeyMap().put(transactionNumCounter, feedbackEntry);
    return transactionKey;
  }

  private synchronized short findAvailableTransactionIdFor(Player player) {
    User user = UserRepository.userOf(player);
    UserMetaConnectionData synchronizeData = user.meta().connectionData();
    Map<Short, TFRequest<?>> transactionFeedBackMap = synchronizeData.transactionShortKeyMap();
    short counter = TRANSACTION_MIN_CODE;
    while (transactionFeedBackMap.containsKey(counter)) counter++;
    return counter;
  }

  private void countTransactionPacket(Player receiver) {
    User user = userOf(receiver);
    UserMetaConnectionData connectionData = user.meta().connectionData();
    connectionData.transactionPacketCounter++;

    if (AccessHelper.now() - connectionData.transactionPacketCounterReset > 3000) {
      System.out.println(connectionData.transactionPacketCounter + " transactions last 3 seconds");
      connectionData.transactionPacketCounter = 0;
      connectionData.transactionPacketCounterReset = AccessHelper.now();
    }
  }

  private void sendTransactionPacket(Player receiver, short id) {
    PacketContainer transactionPacket = protocolManager.createPacket(PacketType.Play.Server.TRANSACTION);
    transactionPacket.getIntegers().write(0, 0);
    transactionPacket.getShorts().write(0, id);
    transactionPacket.getBooleans().write(0, false);
    sendPacket(receiver, transactionPacket);
  }

  private void sendPacket(Player receiver, PacketContainer packet){
    try {
      protocolManager.sendServerPacket(receiver, packet);
    } catch (InvocationTargetException exception) {
      exception.printStackTrace();
    }
  }

  private static long pendingTransactions(User user) {
    return user.meta().connectionData().transactionShortKeyMap().size();
  }

  public User userOf(Player player) {
    return UserRepository.userOf(player);
  }

  public static class TransactionOptions {
    public static int SELF_SYNCHRONIZATION = 1;
    public static int APPEND_ON_OVERFLOW = 2;
    public static int APPEND = 4;

    public static boolean matches(int option, int options) {
      return (options & option) != 0;
    }
  }
}