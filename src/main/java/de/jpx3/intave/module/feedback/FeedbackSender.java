package de.jpx3.intave.module.feedback;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static com.comphenix.protocol.PacketType.Play.Server.PING;
import static com.comphenix.protocol.PacketType.Play.Server.TRANSACTION;
import static de.jpx3.intave.module.feedback.FeedbackOptions.*;

public final class FeedbackSender extends Module {
  public static final short MIN_USER_KEY = 1;
  public static final short MAX_USER_KEY = 24000;
  public static final int PING_MASK = 0xf5550000;
  private static final boolean USE_PING_PONG_PACKETS = MinecraftVersions.VER1_17_0.atOrAbove();
  private static final long OPTIONAL_PENDING_LIMIT = 20;
  private static final long OPTIONAL_SENT_LIMIT = 200;

  private final ProtocolManager protocol = ProtocolLibrary.getProtocolManager();
  private boolean dumpFeedback;

  @Override
  public void enable() {
    dumpFeedback = plugin.settings().getBoolean("logging.feedback-dump", false);
  }

  public <T> void doubleSynchronize(
    Player player, PacketEvent event, T target,
    FeedbackCallback<T> firstCallback, FeedbackCallback<T> secondCallback
  ) {
    tracedDoubleSynchronize(player, event, target, firstCallback, secondCallback, null, null);
  }

  public <T> void doubleSynchronize(
    Player player, PacketEvent event, T target,
    FeedbackCallback<T> firstCallback, FeedbackCallback<T> secondCallback,
    int options
  ) {
    tracedDoubleSynchronize(player, event, target, firstCallback, secondCallback, null, null, options);
  }

  public <T> void doubleSynchronize(
    Player player, PacketContainer encapsulate, T target,
    FeedbackCallback<T> firstCallback, FeedbackCallback<T> secondCallback
  ) {
    tracedDoubleSynchronize(player, encapsulate, target, firstCallback, secondCallback, null, null, 0);
  }

  public <T> void tracedDoubleSynchronize(
    Player player, PacketEvent event, T target,
    FeedbackCallback<T> firstCallback, FeedbackCallback<T> secondCallback,
    FeedbackObserver firstTracker, FeedbackObserver secondTracker
  ) {
    tracedDoubleSynchronize(player, event, target, firstCallback, secondCallback, firstTracker, secondTracker, 0);
  }

  public <T> void tracedDoubleSynchronize(
    Player player, PacketEvent event, T target,
    FeedbackCallback<T> firstCallback, FeedbackCallback<T> secondCallback,
    FeedbackObserver firstTracker, FeedbackObserver secondTracker,
    int options
  ) {
    tracedDoubleSynchronize(player, event.getPacket(), target, firstCallback, secondCallback, firstTracker, secondTracker, options);
    if (event.isReadOnly()) {
      event.setReadOnly(false);
    }
    event.setCancelled(true);
  }

  public <T> void tracedDoubleSynchronize(
    Player player,
    PacketContainer encapsulate, T target,
    FeedbackCallback<? super T> firstCallback, FeedbackCallback<? super T> secondCallback,
    FeedbackObserver firstTracker, FeedbackObserver secondTracker,
    int options
  ) {
    if (!Bukkit.isPrimaryThread()) {
      if (FeedbackOptions.matches(SELF_SYNCHRONIZATION, options)) {
        Synchronizer.synchronize(() -> tracedDoubleSynchronize(player, encapsulate, target, firstCallback, secondCallback, firstTracker, secondTracker, options));
      } else {
        IntaveLogger.logger().error("Can't perform tick-validation off main thread");
        IntaveLogger.logger().error("Please check if you sent a packet / performed a bukkit player action asynchronously in the following trace:");
        Thread.dumpStack();
        firstCallback.success(player, target);
        secondCallback.success(player, target);
      }
      return;
    }
    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }
    tracedSingleSynchronize(player, target, firstCallback, firstTracker, options);
    user.ignoreNextOutboundPacket();
    sendPacket(player, encapsulate.shallowClone());
    user.receiveNextOutboundPacketAgain();
    tracedSingleSynchronize(player, target, secondCallback, secondTracker, options);
  }

  public void synchronize(Player player, Consumer<Void> callback) {
    synchronize(player, callback, (player1, target) -> target.accept(null));
  }

  public void synchronize(Player player, FeedbackCallback<Object> callback) {
    tracedSingleSynchronize(player, null, callback, null, 0);
  }

  public <T> void synchronize(Player player, T target, FeedbackCallback<T> callback) {
    synchronize(player, target, callback, 0);
  }

  public void synchronize(Player player, FeedbackCallback<Object> callback, int options) {
    tracedSingleSynchronize(player, null, callback, null, options);
  }

  public <T> void synchronize(Player player, T target, FeedbackCallback<T> callback, int options) {
    tracedSingleSynchronize(player, target, callback, null, options);
  }

  public <T> void tracedSingleSynchronize(Player player, T target, FeedbackCallback<T> callback, FeedbackObserver tracker) {
    tracedSingleSynchronize(player, target, callback, tracker, 0);
  }

  public <T> void tracedSingleSynchronize(
    Player player, T target, FeedbackCallback<T> callback, FeedbackObserver tracker, int options
  ) {
    if (!Bukkit.isPrimaryThread()) {
      if (FeedbackOptions.matches(SELF_SYNCHRONIZATION, options)) {
        Synchronizer.synchronize(() -> tracedSingleSynchronize(player, target, callback, tracker, options));
      } else {
        IntaveLogger.logger().error("Can't perform tick-validation off main thread");
        IntaveLogger.logger().error("Please check if you sent a packet / performed a bukkit player action asynchronously in the following trace:");
        Thread.dumpStack();
        callback.success(player, target);
      }
      return;
    }
    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }
    boolean append = false;
    if (FeedbackOptions.matches(APPEND_ON_OVERFLOW, options)) {
      boolean tooManyPending = pendingTransactions(userOf(player)) > OPTIONAL_PENDING_LIMIT;
      boolean sentTooManyRecently = user.meta().connection().transactionPacketCounter > OPTIONAL_SENT_LIMIT;
      append = tooManyPending || sentTooManyRecently;
    }
    if (FeedbackOptions.matches(APPEND, options)) {
      append = true;//pendingTransactions(userOf(player)) > 0;
    }
    if (append) {
      appendRequest(player, target, callback, options);
      return;
    }
    countTransactionPacket(player);
    FeedbackRequest<T> request = createRequest(player, target, callback, tracker, options);
    performRequest(player, request);
  }

  private static final Object FALLBACK_OBJECT = new Object();

  private <T> void appendRequest(
    Player player, T obj, FeedbackCallback<T> callback, int options
  ) {
    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }
    ConnectionMetadata synchronizeData = user.meta().connection();
    Queue<FeedbackRequest<?>> queue = synchronizeData
      .transactionAppendMap()
      .computeIfAbsent(synchronizeData.transactionNumCounter, aLong -> new LinkedBlockingDeque<>());
    if (obj == null) {
      //noinspection unchecked
      obj = (T) FALLBACK_OBJECT;
    }
    queue.add(new FeedbackRequest<>(callback, null, obj, (short) -1, -1, options));
  }

  private /*synchronized*/ <T> FeedbackRequest<T> createRequest(
    Player player, T obj, FeedbackCallback<T> callback, FeedbackObserver tracker, int options
  ) {
    User user = UserRepository.userOf(player);
    ConnectionMetadata connection = user.meta().connection();
    if (obj == null) {
      //noinspection unchecked
      obj = (T) FALLBACK_OBJECT;
    }
    short userKey = findUserKey(player);
    long transactionNumCounter = connection.transactionNumCounter++;
    FeedbackRequest<T> feedbackEntry = new FeedbackRequest<T>(callback, tracker, obj, userKey, transactionNumCounter, options);
    connection.feedbackQueue().add(feedbackEntry);
    return feedbackEntry;
  }

  private /* synchronized */ short findUserKey(Player player) {
    User user = UserRepository.userOf(player);
    ConnectionMetadata connection = user.meta().connection();
    FeedbackQueue feedbackQueue = connection.feedbackQueue();
    int attempts = 1000;
    short counter = MIN_USER_KEY;
    int pending = feedbackQueue.size();
    if (pending > 500 && counter + pending < MAX_USER_KEY) {
      counter += pending;
    }
    while (feedbackQueue.hasUserKey(counter) && counter >= MIN_USER_KEY && counter < MAX_USER_KEY && attempts-- > 0) {
      counter++;
    }
    if (attempts <= 0) {
      // should never ever happen, last resort
      attempts = 1000;
      while (feedbackQueue.hasUserKey(counter) && counter >= MIN_USER_KEY && attempts-- > 0) {
        if (MIN_USER_KEY + pending >= MAX_USER_KEY) {
          break;
        }
        counter = (short) ThreadLocalRandom.current().nextInt(MIN_USER_KEY + pending, MAX_USER_KEY);
      }
      if (attempts <= 0) {
        // should only happen when a player is not responding to any feedback requests
        user.kick("Feedback response overdue");
        return -1;
      }
    }
    if (counter < 0) {
      user.kick("Error in feedback synchronization");
      return -1;
    }
    return counter;
  }

  private void countTransactionPacket(Player receiver) {
    User user = userOf(receiver);
    ConnectionMetadata connectionData = user.meta().connection();
    connectionData.transactionPacketCounter++;

    if (System.currentTimeMillis() - connectionData.transactionPacketCounterReset > 3000) {
      connectionData.transactionPacketCounter = 0;
      connectionData.transactionPacketCounterReset = System.currentTimeMillis();
    }
  }

  // for the billions of transaction packets we send, caching is easy and makes sense
  private final PacketContainer[] PACKET_CACHE = new PacketContainer[256];
  private final PacketContainer[] PACKET_CACHE_NO_PING_MASK = new PacketContainer[256];

  private void performRequest(Player receiver, FeedbackRequest<?> request) {
    if (request == null) {
      return;
    }
    User user = userOf(receiver);
    short id = request.userKey();
    int index = id - MIN_USER_KEY;
    boolean noPingMask = user.meta().protocol().noPingMask();
    PacketContainer packet;
    PacketContainer[] packetCache = noPingMask ? PACKET_CACHE_NO_PING_MASK : PACKET_CACHE;
    packet = index >= packetCache.length || index < 0 ? null : packetCache[index];
    if (packet == null) {
      try {
        if (USE_PING_PONG_PACKETS) {
          packet = protocol.createPacket(PING);
          int sentId = id;
          if (!noPingMask) {
            sentId = sentId | PING_MASK;
          }
          packet.getIntegers().write(0, sentId);
        } else {
          packet = protocol.createPacket(TRANSACTION);
          packet.getIntegers().write(0, 0);
          packet.getShorts().write(0, (short) -id);
          packet.getBooleans().write(0, false);
        }
      } catch (Exception exception) {
        throw new IllegalStateException("Unable to create feedback packet", exception);
      }
      if (index >= 0 && index < packetCache.length) {
        packetCache[index] = packet;
      }
    }
    if (dumpFeedback) {
      Thread.dumpStack();
    }
    Modules.feedbackAnalysis().sentTransaction(user, request);
    if (IntaveControl.DEBUG_FEEDBACK_PACKETS) {
//      System.out.println("Received " + transactionIdentifier + "/" +transactionResponse.num() + " from " + player.getName());
      System.out.println("Sent " + id + "/"+request.num() + " to " + receiver.getName());
    }
    sendPacket(receiver, packet);
    request.sent();
  }

  private void sendPacket(Player receiver, PacketContainer packet) {
    PacketSender.sendServerPacket(receiver, packet);
  }

  private static long pendingTransactions(User user) {
    return user.meta().connection().feedbackQueue().size();
  }

  private User userOf(Player player) {
    return UserRepository.userOf(player);
  }
}