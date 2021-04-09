package de.jpx3.intave.event.service;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.event.packet.*;
import de.jpx3.intave.event.service.transaction.TransactionCallBackData;
import de.jpx3.intave.event.service.transaction.TransactionFeedbackCallback;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaSynchronizeData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.Map;

public final class TransactionFeedbackService implements PacketEventSubscriber {
  private final static long TRANSACTION_TIMEOUT = 3000;
  private final static long TRANSACTION_TIMEOUT_KICK = 8000;
  public final static short TRANSACTION_MIN_CODE = -32768;
  public final static short TRANSACTION_MAX_CODE = -16370;
  private final static ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

  public TransactionFeedbackService(IntavePlugin plugin) {
    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);

//    plugin.getServer().getScheduler().scheduleAsyncRepeatingTask(plugin, this::nettyThreadDump, 20 * 10, 20 * 10);

    plugin.getServer().getScheduler().scheduleAsyncRepeatingTask(plugin, this::checkTransactionTimeout, 20 * 2, 20 * 2);
  }

  private void checkTransactionTimeout() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      User user = UserRepository.userOf(player);
//      player.sendMessage(oldestPendingTransaction(user) + "ms since last transaction");
      if (oldestPendingTransaction(user) > TRANSACTION_TIMEOUT_KICK) {
        System.out.println("[Intave] " + player.getName() + " was not responding to validation packets");

        Synchronizer.synchronize(() -> {
          player.kickPlayer("Missing validation response");
        });
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "TRANSACTION")
    }
  )
  public void onPacketReceiving(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (user == null) {
      return;
    }
    UserMetaSynchronizeData synchronizeData = user.meta().synchronizeData();
    Map<Short, TransactionCallBackData<?>> transactionFeedBackMap = synchronizeData.transactionFeedBackMap();
    Short transactionIdentifier = event.getPacket().getShorts().readSafely(0);
    if (transactionIdentifier <= TRANSACTION_MAX_CODE) {
      TransactionCallBackData<?> transactionResponse = transactionFeedBackMap.remove(transactionIdentifier);
      if (transactionResponse == null) {
        return;
      }

      // order verification

      long expected = synchronizeData.lastReceivedTransactionNum + 1;
      if (transactionResponse.num() != expected) {
        Synchronizer.synchronize(() -> {
          player.kickPlayer("Invalid validation response (received " + transactionResponse.num() + ", but expected " + expected +")");
        });
      }
//      Synchronizer.synchronize(() -> {
//        player.sendMessage(String.valueOf(transactionResponse.num()));
//      });
      synchronizeData.lastReceivedTransactionNum = transactionResponse.num();

      transactionResponse.transactionFeedbackCallback().success(
        player,
        convertInstanceOfObject(transactionResponse.obj())
      );
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY")
    }
  )
  public void cancelAttacksIfTransactionMissing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    if (oldestPendingTransaction(user) > TRANSACTION_TIMEOUT) {
      event.setCancelled(true);
    }
  }

  private void nettyThreadDump() {
    Thread.getAllStackTraces().forEach((thread, stackTraceElements) -> {
      if(thread.getName().toLowerCase(Locale.ROOT).contains("netty")) {
        Exception exception = new Exception();
        System.out.println("[Intave/ThreadDump] Thread " + thread.getName() + " " + thread.getState() + " at execution point");
        exception.setStackTrace(stackTraceElements);
        exception.printStackTrace(new PrintStream(System.err) {
          @Override
          public void println(String x) {
            super.println("[Intave/ThreadDump] " + x);
          }
        });
      }
    });
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_DIG"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ITEM")
    }
  )
  public void on(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (transactionResponseTimeout(user)) {
      event.setCancelled(true);
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

  public <T> void requestPong(Player player, T target, TransactionFeedbackCallback<T> callback) {
    Short id = acquireNewId(player, target, callback);
    if (id != null) {
      sendTransactionPacket(player, id);
    }
  }

  private static boolean transactionResponseTimeout(User user) {
    UserMetaSynchronizeData synchronizeData = user.meta().synchronizeData();
    Map<Short, TransactionCallBackData<?>> transactionFeedBackMap = synchronizeData.transactionFeedBackMap();
    long duration = 0;
    for (TransactionCallBackData<?> value : transactionFeedBackMap.values()) {
      duration = Math.max(duration, value.requested());
    }
    return duration != 0 && AccessHelper.now() - duration > TRANSACTION_TIMEOUT_KICK;
  }

  private static long oldestPendingTransaction(User user) {
    UserMetaSynchronizeData synchronizeData = user.meta().synchronizeData();
    Map<Short, TransactionCallBackData<?>> transactionFeedBackMap = synchronizeData.transactionFeedBackMap();
    long duration = AccessHelper.now();
    for (TransactionCallBackData<?> value : transactionFeedBackMap.values()) {
      duration = Math.min(duration, value.requested());
    }
    return duration == 0 ? 0 : AccessHelper.now() - duration;
  }

  private final static Object FALLBACK_OBJECT = new Object();

  private <T> Short acquireNewId(Player player, T obj, TransactionFeedbackCallback<T> callback) {
    User user = UserRepository.userOf(player);
    if (user == null || !user.hasOnlinePlayer()) {
      return null;
    }
    UserMetaSynchronizeData synchronizeData = user.meta().synchronizeData();
    short transactionCounter = synchronizeData.transactionCounter++;
    long transactionNumCounter = synchronizeData.transactionNumCounter++;
    if (transactionCounter >= TRANSACTION_MAX_CODE) {
      synchronizeData.transactionCounter = TRANSACTION_MIN_CODE;
    }
    //player.setLevel((int) transactionNumCounter);
    if(obj == null) {
      //noinspection unchecked
      obj = (T) FALLBACK_OBJECT;
    }
    TransactionCallBackData<T> feedbackEntry = new TransactionCallBackData<>(callback, obj, transactionNumCounter);
    synchronizeData.transactionFeedBackMap().put(transactionCounter, feedbackEntry);
    return transactionCounter;
  }

  private void sendTransactionPacket(Player receiver, short id) {
    if(!Bukkit.isPrimaryThread()) {
      IntaveLogger.logger().error("Can't perform tick-validation off main thread.");
      IntaveLogger.logger().error("Please check if you sent a packet / performed a bukkit player action asynchronously in the following trace:");
      Thread.dumpStack();
      Synchronizer.synchronize(() -> sendTransactionPacket(receiver, id));
      return;
    }

//    IntaveLogger.logger().globalPrintLn("Transaction packet to " + receiver + ", id: " + id + ")");
    PacketContainer transactionPacket = protocolManager.createPacket(PacketType.Play.Server.TRANSACTION);
    transactionPacket.getIntegers().write(0, 0);
    transactionPacket.getShorts().write(0, id);
    transactionPacket.getBooleans().write(0, false);
    try {
      protocolManager.sendServerPacket(receiver, transactionPacket);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }
}