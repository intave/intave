package de.jpx3.intave.module.feedback;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.diagnostic.LatencyStudy;
import de.jpx3.intave.diagnostic.message.DebugBroadcast;
import de.jpx3.intave.diagnostic.message.MessageCategory;
import de.jpx3.intave.diagnostic.message.MessageSeverity;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.pe.PacketEventsBuffers;
import de.jpx3.intave.module.linker.packet.pe.PacketEventsIdMapper;
import de.jpx3.intave.module.linker.packet.pe.PacketEventsSender;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.DelayQueue;

import static de.jpx3.intave.access.player.trust.TrustFactor.RED;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class PacketDelayer extends Module {
  private boolean reverseBlink;
  private boolean reverseLag;
  private boolean lowTolerance;

  @Override
  public void enable() {
    Timer timerCheck = plugin.checks().searchCheck(Timer.class);
    this.reverseBlink = timerCheck.reverseBlink();
    this.reverseLag = timerCheck.reverseLag();
    this.lowTolerance = timerCheck.lowToleranceMode();
  }

//  @PacketSubscription(
//    priority = ListenerPriority.LOWEST,
//    packetsIn = {
//      USE_ENTITY
//    }
//  )
//  public void microLagDelayAttack(PacketEvent event) {
//    Player player = event.getPlayer();
//    User user = UserRepository.userOf(player);
//    ConnectionMetadata connection = user.meta().connection();
//    MovementMetadata movement = user.meta().movement();
//
//    PacketContainer packetContainer = event.getPacket();
//    PacketType packetType = event.getPacketType();
//
//    if (user.justJoined() || !(microLag) || user.trustFactor().atLeast(TrustFactor.YELLOW)) {
//      return;
//    }
//
//    if (connection.eligibleForTransactionTimeout) {
//      // is lagging
//      boolean delayAttack = false;
//
//      if (delayAttack) {
//        connection.attacksQueued++;
//        event.setCancelled(true);
//      }
//    }
//  }

  /**
   * This subscription is not an observer: it is a packet <em>store and replay</em> buffer. It
   * cancels an outgoing packet, parks it in {@code ConnectionMetadata#enqueuedPackets()} /
   * {@code delayedPackets()}, and later pushes it back out through
   * {@link #sendPacket(Player, Object)}.
   * <p>
   * On this engine the parked thing is the raw NMS object {@code PacketContainer#getHandle} returns,
   * and it goes back onto the wire with
   * {@code ProtocolManager#sendServerPacket(player, packet, filters = true)} after
   * {@code PacketContainer.fromPacket} re-wraps it. The {@code ignorePacketEnqueue} flag around each
   * re-emission is what stops the filtered send from re-entering this very subscription.
   * <p>
   * Everything below the two engine specific steps - reading the addressed entity id, taking the
   * parked copy, cancelling - is in {@link #handleOutgoingPacket(Player, HeldOutgoingPacket)}, which
   * the PacketEvents twin shares byte for byte. See that twin for the second half of the design.
   */
  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsOut = {
      SPAWN_ENTITY,
      SPAWN_ENTITY_EXPERIENCE_ORB,
      SPAWN_ENTITY_LIVING,
      NAMED_ENTITY_SPAWN,
      SPAWN_ENTITY_PAINTING,
      SPAWN_ENTITY_WEATHER,
      ENTITY_LOOK,
      ENTITY_MOVE_LOOK,
      REL_ENTITY_MOVE,
      REL_ENTITY_MOVE_LOOK,
      ENTITY_DESTROY,
      ENTITY_STATUS,
      ENTITY_METADATA,
      ENTITY_EQUIPMENT,
      ENTITY_HEAD_ROTATION,
      ENTITY_TELEPORT,
      ENTITY_VELOCITY,
      ENTITY_SOUND,
      ENTITY_EFFECT,
      REMOVE_ENTITY_EFFECT,
      WORLD_PARTICLES,
      CUSTOM_SOUND_EFFECT,
      NAMED_SOUND_EFFECT,
      ANIMATION,
      CHAT_OUT,
      // required for spawn entity player consistency logic
      PLAYER_INFO,
      PLAYER_INFO_REMOVE
    }
  )
  public void enqueueOutgoingPackets(PacketEvent event) {
    Player player = event.getPlayer();

    PacketContainer packetContainer = event.getPacket();
    PacketType packetType = event.getPacketType();

//    if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO) {
////      connection.lastRespawn = System.currentTimeMillis();
//      System.out.println("Player info packet for " + event.getPacket().getPlayerInfoDataLists().read(0));
//      Thread.dumpStack();
//    }

//    if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_REMOVE) {
//      System.out.println("Player info remove packet for " + event.getPacket().getEntityModifier(player.getWorld()).read(0).getUniqueId());
//    }

    // spawn player
//    if (packetType == PacketType.Play.Server.NAMED_ENTITY_SPAWN) {
//      System.out.println("Named entity spawn packet for " + packetContainer.getUUIDs().read(0));
//      Thread.dumpStack();
//    }

    handleOutgoingPacket(player, new ProtocolLibHeldPacket(event, packetContainer, packetType, player));
  }

  /**
   * PacketEvents twin of {@link #enqueueOutgoingPackets(PacketEvent)}.
   * <p>
   * This was previously written off as impossible on the grounds that a cancelled outbound packet
   * has no way back onto the wire except {@code User#sendPacket(Object)}, which re-enters the
   * encoder, and {@code User#sendPacketSilently}, which takes a decoded {@code PacketWrapper} only.
   * Both of those are true and neither is the whole story:
   * {@code ProtocolManager#sendPacketSilently(Object channel, Object buffer)} takes an
   * <em>already encoded</em> buffer and writes it from the encoder's own pipeline context, past
   * every PacketEvents listener. That is exactly what a store and replay buffer needs, and it is
   * wrapped as {@link PacketEventsSender#sendServerBufferWithoutEvent}. The parked thing here is
   * therefore {@code getFullBufferClone()} - the packet's bytes - not an NMS object, and the buffer
   * never re-enters this subscription when it is released.
   *
   * <h2>Why the queue is shared rather than duplicated</h2>
   * {@code ConnectionMetadata#enqueuedPackets()} is not private to this module:
   * {@code FeedbackReceiver#attackHasToBeCancelled} cancels a player's attacks for as long as it is
   * non-empty. A second, PacketEvents-only queue would leave that check reading an always empty
   * ProtocolLib queue, so on this engine a player could keep hitting while his view of the world was
   * being held back - a silent, engine dependent difference in an anticheat, and the exact class of
   * divergence this port exists to avoid. The queue is therefore the same one, and since it is typed
   * {@code Deque<Object>} it already admits both shapes. What makes both shapes safe is the single
   * drain site {@link #sendPacket(Player, Object)}: a {@link PacketEventsDelayedBuffer} is written
   * as bytes, and anything else takes the untouched ProtocolLib branch. {@code EngineSelection} runs
   * one engine per boot, so a queue never actually holds both shapes; the type test is a guard
   * against a mistake, not a dispatch between two live paths, and the ProtocolLib path stays exactly
   * what it was.
   *
   * <h2>Buffer handling</h2>
   * The two buffer traps are not solved here - they are solved in
   * {@code PacketEventsBuffers#encodeIntoEventBuffer}, which this calls before cloning. Read that
   * javadoc for the details: {@code getFullBufferClone()} copies {@code readerIndex..writerIndex},
   * decoding a wrapper consumes the payload, and setter writes never reach the buffer of a cancelled
   * event.
   * <p>
   * This handler avoids the decode side of that entirely. The one field it needs is the entity id
   * the packet addresses, and it reads it straight off the buffer - restoring the reader index
   * afterwards - rather than through a wrapper. That is not a micro-optimisation: this buffer covers
   * twenty-seven packet types and never looks inside them, and building a wrapper would force a
   * decode/re-encode round trip through PacketEvents' codecs (entity metadata, particles, sounds) on
   * every packet it parks. A round trip that is faithful for the two or three packets the sandwich
   * touches is not something to bet a whole outbound stream on. Nothing is decoded here, so the
   * clone is the server's own bytes.
   * <p>
   * {@code encodeIntoEventBuffer} is still called, and it matters in the one case that is not under
   * this handler's control: another plugin's listener may have decoded the packet before Intave's
   * LOWEST listener ran, leaving the reader index past the payload. It is a no-op when nobody did
   * (it returns false and never touches the buffer), and it is only ever called on a packet that is
   * about to be cancelled, so it can never alter bytes that go out through the normal path. When a
   * foreign wrapper is present the raw entity id read cannot be trusted either, and the handler then
   * declines to buffer that packet at all rather than park a copy it cannot vouch for.
   *
   * <h2>One clone per re-emission</h2>
   * Each park site takes its own {@code getFullBufferClone()} instead of sharing one. A packet can
   * legitimately be parked twice in one call - the blink queue and the lag delay queue both accept
   * it when {@code reverseBlink} and {@code reverseLag} are on together - and ProtocolLib's shared
   * NMS handle survives being sent twice, while a netty buffer does not: the first write releases
   * it. Two parks mean two buffers, so every parked entry is written exactly once, and the packet
   * reaches the client the same number of times it would have on ProtocolLib.
   * <p>
   * {@code getFullBufferClone()} is safe to call repeatedly: it copies out of the event buffer with
   * an absolute {@code getBytes}, so it moves no index and each call returns an independent
   * {@code Unpooled.buffer()}.
   *
   * <h2>Ordering</h2>
   * Every outbound packet of one connection passes through its channel's event loop, and so does
   * every inbound one, so park, drain and the emptiness test {@code FeedbackReceiver} makes all run
   * on a single thread per player - the queue order is the wire order. The re-emitted bytes are
   * written from the encoder's context, which puts them downstream of this listener's own packet,
   * exactly where ProtocolLib's filtered re-send puts them.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.LOWEST,
    packetsOut = {
      SPAWN_ENTITY,
      SPAWN_ENTITY_EXPERIENCE_ORB,
      SPAWN_ENTITY_LIVING,
      NAMED_ENTITY_SPAWN,
      SPAWN_ENTITY_PAINTING,
      SPAWN_ENTITY_WEATHER,
      ENTITY_LOOK,
      ENTITY_MOVE_LOOK,
      REL_ENTITY_MOVE,
      REL_ENTITY_MOVE_LOOK,
      ENTITY_DESTROY,
      ENTITY_STATUS,
      ENTITY_METADATA,
      ENTITY_EQUIPMENT,
      ENTITY_HEAD_ROTATION,
      ENTITY_TELEPORT,
      ENTITY_VELOCITY,
      ENTITY_SOUND,
      ENTITY_EFFECT,
      REMOVE_ENTITY_EFFECT,
      WORLD_PARTICLES,
      CUSTOM_SOUND_EFFECT,
      NAMED_SOUND_EFFECT,
      ANIMATION,
      CHAT_OUT,
      // required for spawn entity player consistency logic
      PLAYER_INFO,
      PLAYER_INFO_REMOVE
    }
  )
  public void enqueueOutgoingPackets(PacketSendEvent event, Player player) {
    if (player == null || event.getByteBuf() == null) {
      return;
    }
    handleOutgoingPacket(player, new PacketEventsHeldPacket(event, player));
  }

  /**
   * The two operations this buffer performs on a packet, and the only two that differ per engine.
   * Everything else it does is arithmetic over the player's latency metadata.
   */
  private interface HeldOutgoingPacket {
    /**
     * @return true when the packet's first field is an entity id and that id is the receiving
     * player's own, or when the packet is of such a type and the id could not be established. Both
     * answers mean the same thing to the caller: leave this packet alone. Holding back a player's
     * own entity packets delays his own animations, status and velocity, which is why the buffer has
     * always skipped them; "could not be established" resolves the same way, because guessing wrong
     * in the other direction is the one that breaks the player.
     */
    boolean addressedToSelf();

    /**
     * @return a parked copy of the packet, good for exactly one re-emission through
     * {@link #sendPacket(Player, Object)}. Called once per queue entry, never shared between two.
     */
    Object park();

    /** Stops the packet, so the queue is the only thing that can deliver it. */
    void cancel();
  }

  /**
   * Engine independent body of {@link #enqueueOutgoingPackets(PacketEvent)} and its PacketEvents
   * twin. Transcribed unchanged from the ProtocolLib version it used to be inline in; the only
   * substitutions are the three {@link HeldOutgoingPacket} operations, each standing exactly where
   * a {@code PacketContainer} or {@code PacketEvent} call stood - one self-address test, three park
   * sites, three cancels.
   */
  private void handleOutgoingPacket(Player player, HeldOutgoingPacket held) {
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    ConnectionMetadata connection = meta.connection();
    ProtocolMetadata protocol = meta.protocol();
    SimulationEnvironment movement = meta.movement();

    if (user.justJoined() || !(reverseBlink || reverseLag)) {
      return;
    }

    if (connection.ignorePacketEnqueue) {
      return;
    }

    long playerLatencyGain = connection.transactionPingAverage() - LatencyStudy.pingAverage();
    boolean lowToleranceMode = lowTolerance && user.trustFactor().atOrBelow(RED);
    boolean significantPingGain = playerLatencyGain * (lowToleranceMode ? 1.5 : 1) > user.trustFactorSetting("timer.pg"); // ping gain
    boolean delayRequested = System.currentTimeMillis() - connection.lastDelayRequest < 60 * 1000;
    boolean delayPackets = significantPingGain || delayRequested;

    long lastMovementPacket = System.currentTimeMillis() - connection.lastMovementPacket();
    long oldestTransactionPacket = oldestPendingTransaction(user);
    long positionTimeoutTolerance = protocol.emptyFlyingPacketsAreExplicitlySent() ? 0 : 1100;

    long lagTolerance = user.trustFactorSetting("timer.lt");

    boolean transactionTimeout = oldestTransactionPacket * (lowToleranceMode ? 1.25 : 1) > lagTolerance + connection.transactionPingAverage() + ((double)LatencyStudy.pingAverage() / 2d);
    boolean activeExclude = movement.isInVehicle() || meta.abilities().inGameModeIncludePending(AbilityTracker.GameMode.SPECTATOR);
    long positionBlockTolerance = connection.transactionPingAverage() + LatencyStudy.pingAverage() / 2 + lagTolerance + positionTimeoutTolerance;
    boolean positionTimeout = !activeExclude && lastMovementPacket > positionBlockTolerance;

    if (held.addressedToSelf()) {
      return;
    }

    Deque<Object> enqueuedPackets = connection.enqueuedPackets();
    DelayQueue<DelayedPacket> delayedPackets = connection.delayedPackets();
    boolean tooManyPackets = enqueuedPackets.size() > 8000;
    boolean requestBuffer = (transactionTimeout || positionTimeout);

    if (!requestBuffer && connection.lastBlinkState) {
      connection.blinkDeactivated = System.currentTimeMillis();
    }
    connection.lastBlinkState = requestBuffer;

    long afterBlink = enqueuedPackets.size() > 500 ? 750 : 250;
    long sinceLastRespawn = System.currentTimeMillis() - connection.lastRespawn;

    boolean activatePacketBuffer = !player.isDead() && !tooManyPackets && sinceLastRespawn > 3000
      && (requestBuffer || (System.currentTimeMillis() - connection.blinkDeactivated < afterBlink));

    if (activatePacketBuffer && reverseBlink) {
      // put all delayed packets into the enqueuedPacket queue
      if (!delayedPackets.isEmpty()) {
        DelayedPacket[] delayedObjectsArray = delayedPackets.toArray(new DelayedPacket[0]);
        delayedPackets.clear();
        for (DelayedPacket delayedPacket : delayedObjectsArray) {
          enqueuedPackets.offerLast(delayedPacket.packet());
        }
      }
      if (enqueuedPackets.isEmpty()) {
        connection.firstEnqueue = System.currentTimeMillis();
      }
      enqueuedPackets.offerLast(held.park());
      connection.lastBufferEnqueue = System.currentTimeMillis();
      held.cancel();
    } else if (!enqueuedPackets.isEmpty()) {
      int enqueuedPacketAmount = enqueuedPackets.size();
      if (enqueuedPacketAmount > 100) {
        // send up to 10 packets in the queue by poll
        for (int i = 0; i < 10; i++) {
          Object packet = enqueuedPackets.pollFirst();
          if (packet == null) break;
          connection.ignorePacketEnqueue = true;
          sendPacket(player, packet);
          connection.ignorePacketEnqueue = false;
        }
        enqueuedPackets.offerLast(held.park());
        held.cancel();
      } else {
        int limit = enqueuedPacketAmount;
        // send all packets in the queue by poll
        while (!enqueuedPackets.isEmpty() && limit-- > 0) {
          Object packet = enqueuedPackets.pollFirst();
          if (packet == null) {
            break;
          }
          connection.ignorePacketEnqueue = true;
          sendPacket(player, packet);
          connection.ignorePacketEnqueue = false;
        }
      }
      if (connection.lastBufferNotification + 30000 < System.currentTimeMillis()) {
        connection.lastBufferNotification = System.currentTimeMillis();
        long delay = System.currentTimeMillis() - connection.firstEnqueue;
        String message = player.getName() + " got " + enqueuedPacketAmount + " packets buffered ("+delay+"ms).";
        String shortMessage = player.getName() + " " + enqueuedPacketAmount + " packets halted";
        MessageSeverity severity = enqueuedPacketAmount > 1000 ? MessageSeverity.MEDIUM : MessageSeverity.LOW;
        DebugBroadcast.broadcast(player, MessageCategory.PKBF, severity, message, shortMessage);
//        SibylBroadcast.broadcast(message);
//        if (IntaveControl.GOMME_MODE) {
//          System.out.println(message);
//        }
//        Bukkit.broadcastMessage(message);
      }
      connection.lastBufferEnqueue = System.currentTimeMillis();
      connection.timestampRequiredForAttack = System.currentTimeMillis() + 250;
    } else if (!delayedPackets.isEmpty()) {
      DelayedPacket obj;
      while ((obj = delayedPackets.poll()) != null) {
        Object packet = obj.packet();
        connection.ignorePacketEnqueue = true;
        sendPacket(player, packet);
        connection.ignorePacketEnqueue = false;
      }
    }
    if (delayPackets && reverseLag) {
      long requestedDelay = Math.max(delayRequested ? 100 : 0, (long) (Math.max(playerLatencyGain, 100) / 2d));
      long delay = Math.min(connection.delayedPackets++ / 2, requestedDelay);
      long scheduledTime = System.nanoTime() + delay * 1_000_000;
      scheduledTime = Math.max(connection.lastDelaySlot + 1, scheduledTime);
      connection.lastDelaySlot = scheduledTime;
      delayedPackets.add(new DelayedPacket(held.park(), scheduledTime));
      held.cancel();
      if (connection.lastDelayNotification + 30000 < System.currentTimeMillis()) {
        connection.lastDelayNotification = System.currentTimeMillis();
        String message = player.getName() + " is being delayed by " + requestedDelay + "ms.";
//        SibylBroadcast.broadcast(message);
        String shortMessage = player.getName() + " " + requestedDelay + "ms delayed";
        MessageSeverity severity = requestedDelay > 50 ? MessageSeverity.MEDIUM : MessageSeverity.LOW;
        DebugBroadcast.broadcast(player, MessageCategory.PKDL, severity, message, shortMessage);
      }
    } else {
      connection.delayedPackets = 0;
    }
  }

  /**
   * Releases one parked packet.
   * <p>
   * The ProtocolLib branch is unchanged and is the only branch a ProtocolLib server ever reaches:
   * nothing on that engine puts a {@link PacketEventsDelayedBuffer} into the queue. The
   * PacketEvents branch writes the parked bytes through {@code sendPacketSilently}, which is the
   * counterpart of {@code sendServerPacket(player, packet, filters = true)} for a buffer - with the
   * one difference that it cannot re-enter the listener chain at all, so where ProtocolLib relies on
   * {@code ignorePacketEnqueue} to keep the re-send out of this subscription, PacketEvents has
   * nothing to keep out. The flag is still set around both, because it costs nothing and the shared
   * body should not have to know which engine it is on.
   * <p>
   * That call also ends this buffer's ownership of the bytes either way: netty releases the buffer
   * after writing it, and releases it itself when the channel has already closed.
   */
  private void sendPacket(Player player, Object packet) {
    if (packet == null) {
      return;
    }
    if (packet instanceof PacketEventsDelayedBuffer) {
      PacketEventsSender.sendServerBufferWithoutEvent(player, ((PacketEventsDelayedBuffer) packet).buffer());
      return;
    }
    ProtocolLibrary.getProtocolManager().sendServerPacket(player, PacketContainer.fromPacket(packet), true);
  }

  private long oldestPendingTransaction(User user) {
    ConnectionMetadata connection = user.meta().connection();
    FeedbackRequest<?> peek = connection.feedbackQueue().peek();
    return peek == null ? 0 : peek.passedTime();
  }

  /** ProtocolLib half of {@link HeldOutgoingPacket}; the calls it makes are the original ones. */
  private static final class ProtocolLibHeldPacket implements HeldOutgoingPacket {
    private final PacketEvent event;
    private final PacketContainer packetContainer;
    private final PacketType packetType;
    private final Player player;

    private ProtocolLibHeldPacket(PacketEvent event, PacketContainer packetContainer,
                                  PacketType packetType, Player player) {
      this.event = event;
      this.packetContainer = packetContainer;
      this.packetType = packetType;
      this.player = player;
    }

    @Override
    public boolean addressedToSelf() {
      boolean idAddressed = packetType == PacketType.Play.Server.ANIMATION ||
        packetType == PacketType.Play.Server.ENTITY_STATUS ||
        packetType == PacketType.Play.Server.ENTITY_METADATA ||
        packetType == PacketType.Play.Server.ENTITY_TELEPORT ||
        packetType == PacketType.Play.Server.ENTITY_VELOCITY;

      if (idAddressed) {
        Integer entityId = packetContainer.getIntegers().read(0);
        return entityId != null && entityId == player.getEntityId();
      }
      return false;
    }

    @Override
    public Object park() {
      return packetContainer.getHandle();
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }
  }

  /**
   * PacketEvents half of {@link HeldOutgoingPacket}: reads the addressed entity id off the wire
   * bytes and parks encoded clones.
   */
  private static final class PacketEventsHeldPacket implements HeldOutgoingPacket {
    private final PacketSendEvent event;
    private final Player player;

    private PacketEventsHeldPacket(PacketSendEvent event, Player player) {
      this.event = event;
      this.player = player;
    }

    /**
     * All five id addressed packets put the entity id first in the payload, and the reader index a
     * send listener is handed sits exactly on that payload. The widths were taken from PacketEvents'
     * own wrappers on 2.13.0: {@code WrapperPlayServerEntityStatus} reads a plain int on every
     * version, the other four read a VarInt on everything from 1.8 up, which is Intave's floor.
     * <p>
     * Reading them here rather than through those wrappers is what keeps the parked bytes the
     * server's own; see the class' twin javadoc. The read moves the reader index and puts it back,
     * so the buffer is in the same state the next listener would have found it in.
     */
    @Override
    public boolean addressedToSelf() {
      PacketTypeCommon type = event.getPacketType();
      boolean plainInt = SelfAddressed.PLAIN_INT.contains(type);
      if (!plainInt && !SelfAddressed.VAR_INT.contains(type)) {
        return false;
      }
      if (event.getLastUsedWrapper() != null) {
        // Somebody decoded this packet before Intave's LOWEST listener ran, so the reader index is
        // no longer on the payload and the id below it cannot be trusted. Decline the packet.
        return true;
      }
      Integer entityId = readLeadingEntityId(event.getByteBuf(), plainInt);
      return entityId == null || entityId == player.getEntityId();
    }

    @Override
    public Object park() {
      // Only ever reached for a packet that is cancelled immediately afterwards, so this can never
      // change bytes that leave through the normal path. It is a no-op unless a foreign listener
      // decoded the packet; see PacketEventsBuffers#encodeIntoEventBuffer.
      PacketEventsBuffers.encodeIntoEventBuffer(event);
      return new PacketEventsDelayedBuffer(event.getFullBufferClone());
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }

    /** @return the id, or null when the buffer is too short or malformed to carry one. */
    private static Integer readLeadingEntityId(Object buffer, boolean plainInt) {
      if (buffer == null) {
        return null;
      }
      int readerIndex = ByteBufHelper.readerIndex(buffer);
      try {
        if (plainInt) {
          return ByteBufHelper.readableBytes(buffer) < Integer.BYTES
            ? null
            : ByteBufHelper.readInt(buffer);
        }
        return ByteBufHelper.isReadable(buffer) ? ByteBufHelper.readVarInt(buffer) : null;
      } catch (RuntimeException exception) {
        return null;
      } finally {
        ByteBufHelper.readerIndex(buffer, readerIndex);
      }
    }
  }

  /**
   * The id addressed packet types, split by how wide their leading entity id is.
   * <p>
   * Held in a nested class so they are resolved the first time the PacketEvents path runs rather
   * than when this module is loaded: {@link PacketEventsIdMapper} picks between candidate constant
   * names by asking the running server version which one it puts on the wire, and resolving before
   * PacketEvents knows that version would bind the wrong name. Going through the mapper rather than
   * naming the constants is also what keeps a constant that a given PacketEvents release does not
   * declare from taking the packet layer down with a {@code NoSuchFieldError}.
   */
  private static final class SelfAddressed {
    static final Set<PacketTypeCommon> PLAIN_INT = typesOf(PacketId.Server.ENTITY_STATUS);
    static final Set<PacketTypeCommon> VAR_INT = typesOf(
      PacketId.Server.ANIMATION,
      PacketId.Server.ENTITY_METADATA,
      PacketId.Server.ENTITY_TELEPORT,
      PacketId.Server.ENTITY_VELOCITY
    );

    private static Set<PacketTypeCommon> typesOf(PacketId.Server... packets) {
      Set<PacketTypeCommon> types = new HashSet<>(8);
      for (PacketId.Server packet : packets) {
        types.addAll(PacketEventsIdMapper.typesOf(packet));
      }
      return types;
    }
  }
}
