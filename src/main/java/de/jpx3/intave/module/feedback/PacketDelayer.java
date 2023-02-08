package de.jpx3.intave.module.feedback;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.diagnostic.LatencyStudy;
import de.jpx3.intave.diagnostic.message.DebugBroadcast;
import de.jpx3.intave.diagnostic.message.MessageCategory;
import de.jpx3.intave.diagnostic.message.MessageSeverity;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.Map;
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
      CHAT_OUT
    }
  )
  public void enqueueOutgoingPackets(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ConnectionMetadata connection = user.meta().connection();
    MovementMetadata movement = user.meta().movement();

    PacketContainer packetContainer = event.getPacket();
    PacketType packetType = event.getPacketType();

    if (user.justJoined() || !(reverseBlink || reverseLag) || user.trustFactor().atLeast(TrustFactor.YELLOW)) {
      return;
    }

    if (connection.ignorePacketEnqueue) {
      return;
    }

    long playerLatencyGain = connection.transactionPingAverage() - LatencyStudy.transactionPingAverage();
    boolean lowToleranceMode = lowTolerance && user.trustFactor().atOrBelow(RED);
    boolean significantPingGain = playerLatencyGain * (lowToleranceMode ? 1.5 : 1) > user.trustFactorSetting("timer.pg"); // ping gain
    boolean delayRequested = System.currentTimeMillis() - connection.lastDelayRequest < 60 * 1000;
    boolean delayPackets = significantPingGain || delayRequested;

    long lastMovementPacket = System.currentTimeMillis() - connection.lastMovementPacket();
    long oldestTransactionPacket = oldestPendingTransaction(user);
    long positionTimeoutTolerance = user.meta().protocol().flyingPacketsAreSent() ? 0 : 1050;

    long lagTolerance = user.trustFactorSetting("timer.lt");
    boolean transactionTimeout = oldestTransactionPacket * (lowToleranceMode ? 1.25 : 1) > connection.transactionPingAverage() + LatencyStudy.transactionPingAverage() / 2 + lagTolerance;
    boolean riding = movement.isInVehicle();
    long positionBlockTolerance = connection.transactionPingAverage() + LatencyStudy.transactionPingAverage() / 2 + lagTolerance + positionTimeoutTolerance;
    boolean positionTimeout = !riding && lastMovementPacket > positionBlockTolerance;

    boolean idAddressed = packetType == PacketType.Play.Server.ANIMATION ||
      packetType == PacketType.Play.Server.ENTITY_STATUS ||
      packetType == PacketType.Play.Server.ENTITY_METADATA ||
      packetType == PacketType.Play.Server.ENTITY_VELOCITY;

    if (idAddressed) {
      Integer entityId = packetContainer.getIntegers().read(0);
      if (entityId != null && entityId == player.getEntityId()) {
        return;
      }
    }

    Deque<Object> enqueuedPackets = connection.enqueuedPackets();
    DelayQueue<DelayedPacket> delayedPackets = connection.delayedPackets();
    boolean tooManyPackets = enqueuedPackets.size() > 8000;
    boolean buffer = !tooManyPackets && !player.isDead() && (transactionTimeout || positionTimeout);

    if (buffer && reverseBlink) {
      // put all delayed packets into the enqueuedPacket queue
      if (!delayedPackets.isEmpty()) {
        DelayedPacket[] delayedObjectsArray = delayedPackets.toArray(new DelayedPacket[0]);
        delayedPackets.clear();
        for (DelayedPacket delayedPacket : delayedObjectsArray) {
          enqueuedPackets.offerLast(delayedPacket.packet());
        }
      }
      enqueuedPackets.offerLast(packetContainer.getHandle());
      connection.lastBufferEnqueue = System.currentTimeMillis();
      event.setCancelled(true);
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
        enqueuedPackets.offerLast(packetContainer.getHandle());
        event.setCancelled(true);
      } else {
        // send all packets in the queue by poll
        while (!enqueuedPackets.isEmpty()) {
          Object packet = enqueuedPackets.pollFirst();
          connection.ignorePacketEnqueue = true;
          sendPacket(player, packet);
          connection.ignorePacketEnqueue = false;
        }
      }
      if (connection.lastBufferNotification + 30000 < System.currentTimeMillis()) {
        connection.lastBufferNotification = System.currentTimeMillis();
        String message = player.getName() + " got " + enqueuedPacketAmount + " packets buffered.";
        String shortMessage = player.getName() + " " + enqueuedPacketAmount + " packets halted";
        MessageSeverity severity = enqueuedPacketAmount > 1000 ? MessageSeverity.MEDIUM : MessageSeverity.LOW;
        DebugBroadcast.broadcast(player, MessageCategory.PKBF, severity, message, shortMessage);
//        SibylBroadcast.broadcast(message);
//        if (IntaveControl.GOMME_MODE) {
//          System.out.println(message);
//        }
      }
      connection.lastBufferEnqueue = System.currentTimeMillis();
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
      delayedPackets.add(new DelayedPacket(packetContainer.getHandle(), scheduledTime));
      event.setCancelled(true);
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

  private void sendPacket(Player player, Object packet) {
    ProtocolLibrary.getProtocolManager().sendServerPacket(player, PacketContainer.fromPacket(packet), true);
  }

  private long oldestPendingTransaction(User user) {
    ConnectionMetadata synchronizeData = user.meta().connection();
    Map<Short, FeedbackRequest<?>> transactionFeedBackMap = synchronizeData.transactionShortKeyMap();
    long duration = System.currentTimeMillis();
    for (FeedbackRequest<?> value : transactionFeedBackMap.values()) {
      duration = Math.min(duration, value.requested());
    }
    return System.currentTimeMillis() - duration;
  }
}
