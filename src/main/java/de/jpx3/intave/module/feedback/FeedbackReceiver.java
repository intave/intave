package de.jpx3.intave.module.feedback;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
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
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
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
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.module.feedback.FeedbackSender.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class FeedbackReceiver extends Module {
  private static final boolean USE_PING_PACKETS = MinecraftVersions.VER1_17_0.atOrAbove();
  private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(8);
  private static final long TIMEOUT_KICK = TimeUnit.SECONDS.toMillis(40);
  private static final long CHECK_TIMEOUT_KICK_TICKS = 20 * 10;

  public FeedbackReceiver(IntavePlugin plugin) {
    Tasks.periodicNamed(
      "FeedbackReceiver.transactionTimeout",
      this::checkTransactionTimeout,
      CHECK_TIMEOUT_KICK_TICKS,
      CHECK_TIMEOUT_KICK_TICKS
    ).startAsync();
    Tasks.periodicNamed(
      "FeedbackReceiver.invalidOrderDecay",
      this::decreaseTAKAVL,
      20 * 60,
      20 * 60
    ).startAsync();
  }

  private void decreaseTAKAVL() {
    UserRepository.applyOnOnlineUsers(user -> {
      user.meta().connection().transactionKeepAliveInvalidOrderVL = Math.max(0, user.meta().connection().transactionKeepAliveInvalidOrderVL - 1);
    });
  }

  private void checkTransactionTimeout() {
    UserRepository.applyOnOnlineUsers(user ->
      Synchronizer.synchronize(user, () -> checkTransactionTimeoutFor(user.player()))
    );
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
    handleInventoryClick(event.getPlayer(), event.getPacket().getShorts().readSafely(0));
  }

  /**
   * PacketEvents twin of the subscription above.
   * <p>
   * The one field the ProtocolLib entry pulls off the packet is {@code getShorts().readSafely(0)},
   * which on a container click is the legacy action number: present up to 1.16, and absent from
   * 1.17 onwards where the field was replaced by the int container state id, so the ProtocolLib
   * read yields null there. PacketEvents models exactly that with
   * {@link WrapperPlayClientClickWindow#getActionNumber()}, an {@link Optional} that is empty on
   * the protocols where the field is gone, so an empty optional is passed on as the same null the
   * ProtocolLib path produces. The full {@link de.jpx3.intave.packet.view.WindowClickView} family
   * is deliberately not used here: its {@code revision()} intentionally reports the modern state id
   * on 1.17+ rather than the legacy action number, which is not the field this subscription reads.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = WINDOW_CLICK
  )
  public void receiveInventoryClick(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) {
      return;
    }
    Object rawPlayer = event.getPlayer();
    if (!(rawPlayer instanceof Player)) {
      return;
    }
    Optional<Integer> actionNumber = new WrapperPlayClientClickWindow(event).getActionNumber();
    handleInventoryClick(
      (Player) rawPlayer,
      actionNumber.isPresent() ? actionNumber.get().shortValue() : null
    );
  }

  /**
   * Engine independent container click handling shared by both entry points above.
   * <p>
   * The transaction id rewriting this subscription exists for is currently commented out, so the
   * body only resolves the user and returns; both engines are kept on it so the id rewrite can be
   * re-enabled once for both at the same time.
   *
   * @param clientTransactionId the legacy action number, or null on protocols that omit the field
   */
  private void handleInventoryClick(Player player, Short clientTransactionId) {
    User user = userOf(player);
    if (clientTransactionId == null) {
      return;
    }
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
  public void onKeepAlive(PacketEvent event) {
    if (!IntaveControl.CLIENT_KEEP_ALIVE_NETTY_CHECK) {
      return;
    }
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    short possibleUserKey = 0;
    if (MinecraftVersions.VER1_12_0.atOrAbove()) {
      possibleUserKey = packet.getLongs().readSafely(0).shortValue();
    } else {
      possibleUserKey = packet.getIntegers().readSafely(0).shortValue();
    }
    handleKeepAlive(player, possibleUserKey);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      KEEP_ALIVE
    }
  )
  public void onKeepAlive(PacketReceiveEvent event, Player player) {
    if (!IntaveControl.CLIENT_KEEP_ALIVE_NETTY_CHECK) {
      return;
    }
    if (player == null) {
      return;
    }
    // PacketEvents already normalises the pre 1.12 varint keep alive id into a long, so the same
    // truncation to the low 16 bits the ProtocolLib path performs applies to every version here.
    handleKeepAlive(player, (short) new WrapperPlayClientKeepAlive(event).getId());
  }

  /**
   * Engine independent keep-alive handling shared by both entry points above. Only the low 16 bits
   * of the keep alive id are consumed, so the id is passed in already truncated.
   */
  private void handleKeepAlive(Player player, short possibleUserKey) {
    User user = userOf(player);
    FeedbackQueue feedbackQueue = user.meta().connection().feedbackQueue();
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
  public void outgoingTransaction(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    handleOutgoingTransaction(
      event.getPlayer(),
      USE_PING_PACKETS ? packet.getIntegers().readSafely(0) : null,
      USE_PING_PACKETS ? null : packet.getShorts().readSafely(0)
    );
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      PacketId.Server.TRANSACTION, PacketId.Server.PING
    }
  )
  public void outgoingTransaction(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    PacketTypeCommon packetType = event.getPacketType();
    if (packetType == PacketType.Play.Server.PING) {
      handleOutgoingTransaction(player, new WrapperPlayServerPing(event).getId(), null);
    } else if (packetType == PacketType.Play.Server.WINDOW_CONFIRMATION) {
      WrapperPlayServerWindowConfirmation wrapper = new WrapperPlayServerWindowConfirmation(event);
      handleOutgoingTransaction(player, wrapper.getWindowId(), wrapper.getActionId());
    }
  }

  /**
   * Engine independent outbound feedback packet handling shared by both entry points above.
   * <p>
   * Both fields the two engines can carry are passed in rather than the packet itself: the ping
   * packet's int id (the window id on a transaction packet) and the transaction packet's short
   * action number, matching what {@code getIntegers().readSafely(0)} and
   * {@code getShorts().readSafely(0)} yielded on the ProtocolLib path. Only the field
   * {@link #USE_PING_PACKETS} selects is read, the other one may be null.
   */
  private void handleOutgoingTransaction(Player player, Integer integerField, Short shortField) {
    User user = UserRepository.userOf(player);
    boolean noPingMask = user.meta().protocol().noPingMask();
    if (!hasValidUserKey(integerField, shortField, noPingMask) && activeGenerator != IdGeneratorMode.highestCompatibility()) {
      short userKey = userKeyFrom(integerField, shortField);
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
  public void receiveAcknowledgementPacket(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    // Only the field the active branch below reads is pulled off the packet, exactly as the
    // inlined body used to; the int one is additionally needed by the debug prints.
    Integer integerField = USE_PING_PACKETS || IntaveControl.DEBUG_FEEDBACK_PACKETS
      ? packet.getIntegers().readSafely(0)
      : null;
    Short shortField = USE_PING_PACKETS ? null : packet.getShorts().readSafely(0);
    if (handleAcknowledgementPacket(event.getPlayer(), integerField, shortField)) {
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      TRANSACTION, PONG
    }
  )
  public void receiveAcknowledgementPacket(PacketReceiveEvent event, Player player) {
    if (player == null) {
      return;
    }
    PacketTypeCommon packetType = event.getPacketType();
    boolean cancel;
    if (packetType == PacketType.Play.Client.PONG) {
      cancel = handleAcknowledgementPacket(player, new WrapperPlayClientPong(event).getId(), null);
    } else if (packetType == PacketType.Play.Client.WINDOW_CONFIRMATION) {
      WrapperPlayClientWindowConfirmation wrapper = new WrapperPlayClientWindowConfirmation(event);
      cancel = handleAcknowledgementPacket(player, wrapper.getWindowId(), wrapper.getActionId());
    } else {
      return;
    }
    if (cancel) {
      event.setCancelled(true);
    }
  }

  /**
   * Engine independent acknowledgement handling shared by both entry points above; see
   * {@link #handleOutgoingTransaction(Player, Integer, Short)} for what the two field parameters
   * carry.
   *
   * @return true when the packet has to be cancelled, which the caller applies on its own event.
   */
  private boolean handleAcknowledgementPacket(Player player, Integer integerField, Short shortField) {
    // viaversion packet limit workaround
    ViaVersionAdapter.decrementReceivedPackets(player, 2);

    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return false;
    }
    MetadataBundle meta = user.meta();
    ConnectionMetadata connection = meta.connection();
    FeedbackQueue feedbackQueue = connection.feedbackQueue();
    boolean noPingMask = user.meta().protocol().noPingMask();

    if (!hasValidUserKey(integerField, shortField, noPingMask)) {
      if (IntaveControl.DEBUG_FEEDBACK_PACKETS) {
        System.out.println("Received " + integerField + " from " + player.getName() + " but no user key was found");
      }
      return false;
    }

    short userKey = userKeyFrom(integerField, shortField);
    FeedbackRequest<?> response = feedbackQueue.peek(userKey);
    if (response == null) {
      if (IntaveControl.DEBUG_FEEDBACK_PACKETS) {
        System.out.println("Received " + userKey + "/" + integerField + " from " + player.getName() + " but no request was found");
      }
      return false;
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
    return true;
  }

  private short userKeyFrom(Integer integerField, Short shortField) {
    if (USE_PING_PACKETS) {
      int inputInteger = integerField;
      return (short) (inputInteger & 0xffff);
    } else {
      return shortField;
    }
  }

  private boolean hasValidUserKey(Integer integerField, Short shortField, boolean noPingMask) {
    short shortInput;
    if (USE_PING_PACKETS) {
      int inputInteger = integerField;
      if ((inputInteger & 0xffff0000) != PING_MASK && !noPingMask) {
        return false;
      }
      shortInput = (short) (inputInteger & 0xffff);
    } else {
      shortInput = shortField;
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
    if (attackHasToBeCancelled(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void cancelAttacksIfTransactionMissing(PacketReceiveEvent event) {
    Object player = event.getPlayer();
    if (!(player instanceof Player)) {
      return;
    }
    if (attackHasToBeCancelled((Player) player)) {
      event.setCancelled(true);
    }
  }

  /**
   * Engine independent decision shared by both entry points above. The packet body is never read
   * here, only the player's transaction state, so no packet view is needed.
   */
  private boolean attackHasToBeCancelled(Player player) {
    User user = UserRepository.userOf(player);
    ConnectionMetadata connection = user.meta().connection();
    connection.eligibleForTransactionTimeout = true;
    return oldestPendingTransaction(user) > TIMEOUT ||
      // Logically, this is part of the PacketDelayer,
      // but I've put this stuff in this if-clause to have a common place for
      // any transaction-related attack cancels
      !connection.enqueuedPackets().isEmpty() ||
      System.currentTimeMillis() - connection.timestampRequiredForAttack < 0;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsIn = {
      BLOCK_DIG, BLOCK_PLACE, USE_ITEM
    }
  )
  public void cancelInteractionsOnTimeout(PacketEvent event) {
    if (interactionHasToBeCancelled(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGHEST,
    packetsIn = {
      BLOCK_DIG, BLOCK_PLACE, USE_ITEM
    }
  )
  public void cancelInteractionsOnTimeout(PacketReceiveEvent event) {
    Object player = event.getPlayer();
    if (!(player instanceof Player)) {
      return;
    }
    if (interactionHasToBeCancelled((Player) player)) {
      event.setCancelled(true);
    }
  }

  /** Engine independent decision shared by both entry points above; reads no packet field. */
  private boolean interactionHasToBeCancelled(Player player) {
    User user = UserRepository.userOf(player);
    user.meta().connection().eligibleForTransactionTimeout = true;
    return oldestPendingTransaction(user) > TIMEOUT * 2;
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
