package de.jpx3.intave.user.meta;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.module.feedback.DelayedPacket;
import de.jpx3.intave.module.feedback.FeedbackRequest;
import de.jpx3.intave.module.tracker.entity.EntityShade;
import de.jpx3.intave.packet.PacketSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ThreadLocalRandom;

@Relocate
public final class ConnectionMetadata {
  private final Player player;
  private final Map<Short, FeedbackRequest<?>> transactionShortMap = Maps.newConcurrentMap();
  private final Map<Long, FeedbackRequest<?>> transactionGlobalKeyMap = Maps.newConcurrentMap();
  private final Map<Long, Queue<FeedbackRequest<?>>> transactionOptionalAppendixMap = Maps.newConcurrentMap();
  private final Map<Integer, EntityShade> entitiesById = Maps.newConcurrentMap();
  private final Set<Integer> entityIds = new HashSet<>();
  private final List<EntityShade> entities = Lists.newCopyOnWriteArrayList();
  private final List<EntityShade> synchronizedEntities = Lists.newCopyOnWriteArrayList();
  private final Map<Long, Long> remainingPingPacketTimestamps = Maps.newConcurrentMap();
  private final List<Long> latencyDifferenceBalance = Lists.newCopyOnWriteArrayList();
  public long lastCCCInfoMessageSent = 0;
  public boolean sendAsyncMessage = false;
  public boolean eligibleForTransactionTimeout = false;
  public int speculativeMovementTicks = 0;
  public int randomTransactionIdShift = ThreadLocalRandom.current().nextInt(1, 2000);

  private final Deque<Object> bufferEnqueue = new ArrayDeque<>(8500);
  private final DelayQueue<DelayedPacket> delayQueue = new DelayQueue<>();
  public long lastBufferNotification = 0;
  public long lastDelayNotification = 0;
  public long lastDelaySlot = 0;
  public long lastBufferEnqueue = 0;
  public boolean ignorePacketEnqueue;
  public long delayedPackets = 0;
  public long lastDelayRequest = 0;

  // Client Synchronization
  public int latency;
  public long lastKeepAliveDifference;
  public int latencyJitter;
  public short transactionCounter = Short.MIN_VALUE;
  public long transactionNumCounter = 0;
  public long lastReceivedTransactionNum = -1;
  public long lastSynchronization = System.currentTimeMillis();
  public long transactionPacketCounter;
  public long transactionPacketCounterReset;

  public long hardTransactionResponse = 0;

  // Lag identification
  private long lastMovementTimestamps;
  private final List<Long> movementLagSpikeHistory = new ArrayList<>();

  public ConnectionMetadata(Player player) {
    this.player = player;
  }

  @DispatchTarget
  public void receiveMovement() {
    long now = System.currentTimeMillis();
    if (this.lastMovementTimestamps != 0) {
      long difference = now - lastMovementTimestamps;
      movementLagSpikeHistory.add(difference);
      if (movementLagSpikeHistory.size() > 3) {
        movementLagSpikeHistory.remove(0);
      }
    }
    this.lastMovementTimestamps = now;
  }

  public long lastMovementPacket() {
    return lastMovementTimestamps;
  }

  public double averageMovementPacketTimestamp() {
    return averageOf(movementLagSpikeHistory);
  }

  private double averageOf(List<? extends Number> data) {
    double sum = 0;
    for (Number element : data) {
      sum += element.doubleValue();
    }
    if (sum == 0) {
      return 0;
    }
    return sum / data.size();
  }

  private long transactionSum = 0;
  private long transactionNum = 0;

  public void receivedTransactionAfter(long milliseconds) {
    transactionSum += Math.min(milliseconds, 1000);
    transactionNum++;
    if (transactionNum > Short.MAX_VALUE / 2) {
      transactionSum /= 2;
      transactionNum /= 2;
    }
    if (IntaveControl.LATENCY_PING_AS_XP_LEVEL && transactionNum % 10 == 0) {
      sendPacketWithExperience(player, (int) transactionPingAverage());
    }
  }

  private void sendPacketWithExperience(Player player, int level) {
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.EXPERIENCE);
    packet.getFloat().write(0, 0f);
    packet.getIntegers().write(0, 0);
    packet.getIntegers().write(1, level);
    PacketSender.sendServerPacket(player, packet);
  }

  public long transactionPingAverage() {
    return transactionNum == 0 ? 0 : transactionSum / transactionNum;
  }

//  public void receivedTransactionAfter(long milliseconds) {
//    if (transactionPings.size() > 1024 * 8) {
//      transactionPings.remove(0);
//    }
//    transactionPings.add(milliseconds);
//  }
//
//  private long transactionPingCache = -1;
//  private long lastTPCRefresh = 0;
//
//  public long transactionPingAverage() {
//    if (System.currentTimeMillis() - lastTPCRefresh > 5000) {
//      long sum = 0;
//      for (Long transactionPing : transactionPings) {
//        sum += Math.min(transactionPing, 500);
//      }
//      lastTPCRefresh = System.currentTimeMillis();
//      transactionPingCache = sum / transactionPings.size();
//    }
//    return transactionPingCache;
//  }

  public Map<Short, FeedbackRequest<?>> transactionShortKeyMap() {
    return transactionShortMap;
  }

  public Map<Long, FeedbackRequest<?>> transactionGlobalKeyMap() {
    return transactionGlobalKeyMap;
  }

  public Map<Long, Queue<FeedbackRequest<?>>> transactionAppendMap() {
    return transactionOptionalAppendixMap;
  }

  @Deprecated
  public Map<Integer, EntityShade> entitiesById() {
    return entitiesById;
  }

  public Collection<EntityShade> entities() {
    return entities;
  }

  public EntityShade entityBy(int identifier) {
    return entitiesById.get(identifier);
  }

  public void destroyEntity(int entityId) {
    entitiesById.put(entityId, EntityShade.destroyedEntity());
    entityIds.remove(entityId);

    // we will not override the entity collection, as it would require a lot of performance and seems quite redundant in the first place
//    for (int i = 0, entitiesSize = entities.size(); i < entitiesSize; i++) {
//      EntityShade entity = entities.get(i);
//      if (entity.entityId() == entityId) {
//        entities.set(i, EntityShade.destroyedEntity());
//      }
//    }

    // using removeIf requires the least amount of locking and array modifications for CopyOnWriteArrayLists
    entities.removeIf(entity -> entity.entityId() == entityId);
  }

  public DelayQueue<DelayedPacket> delayedPackets() {
    return delayQueue;
  }

  public void enterEntity(EntityShade entity) {
    entitiesById.put(entity.entityId(), entity);
    entityIds.add(entity.entityId());
    entities.add(entity);
  }

  public List<EntityShade> tracedEntities() {
    return synchronizedEntities;
  }

  public Map<Long, Long> pingPackets() {
    return remainingPingPacketTimestamps;
  }

  public List<Long> latencyDifferenceBalance() {
    return latencyDifferenceBalance;
  }

  public Deque<Object> enqueuedPackets() {
    return bufferEnqueue;
  }

  public Set<Integer> entityIds() {
    return entityIds;
  }
}