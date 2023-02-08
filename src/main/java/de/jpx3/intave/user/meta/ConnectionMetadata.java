package de.jpx3.intave.user.meta;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.module.feedback.DelayedPacket;
import de.jpx3.intave.module.feedback.FeedbackRequest;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.PacketSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ThreadLocalRandom;

@Relocate
public final class ConnectionMetadata {
  private final Player player;
  private final Map<Long, FeedbackRequest<?>> transactionGlobalKeyMap = Maps.newConcurrentMap();
  private final Map<Short, FeedbackRequest<?>> transactionShortMap = Maps.newConcurrentMap();
  private final Map<Long, Queue<FeedbackRequest<?>>> transactionOptionalAppendixMap = Maps.newConcurrentMap();
  private final Map<Integer, Entity> entitiesById = Maps.newConcurrentMap();
  private final Set<Integer> entityIds = new HashSet<>();
  private final List<Entity> entities = Lists.newCopyOnWriteArrayList();
  private final List<Entity> synchronizedEntities = Lists.newCopyOnWriteArrayList();
  private final Map<Long, Long> remainingPingPacketTimestamps = Maps.newConcurrentMap();
  private final List<Long> latencyDifferenceBalance = Lists.newCopyOnWriteArrayList();

  // not used
  private final Map<Integer, Integer> localEntityIdsToGlobalIds = Maps.newConcurrentMap();
  private final Map<Integer, Integer> globalEntityIdsToLocalIds = Maps.newConcurrentMap();
  private final Set<Integer> globalEntityIdsForRemoval = new HashSet<>();

  public final Map<Integer, Integer> duplicationOwners = new HashMap<>();
  public final Map<Integer, DecoySide> decoySides = new HashMap<>();
  public final Set<Integer> duplicatedEntityIds = new HashSet<>();
  public final Set<Integer> shouldNotBeAttacked = new HashSet<>();
  @Deprecated
  public boolean markAttackInvalid;

  public int windowClickId;

  public enum DecoySide {
    FIRST_IS_DECOY,
    SECOND_IS_DECOY,
  }

  public int pendingTransactions;

//  private final Set<Integer> takenLocalEntityIds = new HashSet<>();
  private int localEntityIdCounter = 1;
  public long lastCCCInfoMessageSent = 0;
  public boolean sendAsyncMessage = false;
  public boolean eligibleForTransactionTimeout = false;
  public int speculativeMovementTicks = 0;
  public int randomTransactionIdShift = ThreadLocalRandom.current().nextInt(1, 2000);
  public int attacksQueued;
  public long lastAttackQueueRequest;

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

  // labymod data
  public JsonObject labyModData = new JsonObject();

  public ConnectionMetadata(Player player) {
    this.player = player;

    this.globalEntityIdsToLocalIds.put(-1, -1);
    this.localEntityIdsToGlobalIds.put(-1, -1);
    this.globalEntityIdsToLocalIds.put(0, 0);
    this.localEntityIdsToGlobalIds.put(0, 0);
    if (player != null) {
      int entityId = player.getEntityId();
      this.globalEntityIdsToLocalIds.put(entityId, entityId);
      this.localEntityIdsToGlobalIds.put(entityId, entityId);
    }
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
    if (transactionNum > Short.MAX_VALUE / 8) {
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

  public Integer globalEntityIdFromLocal(Integer localEntityId) {
    return localEntityIdsToGlobalIds.getOrDefault(localEntityId, -1);
  }

  public Integer localEntityIdFromGlobal(Integer globalEntityId) {
    return globalEntityIdsToLocalIds.getOrDefault(globalEntityId, -1);
  }

  public Map<Integer, Integer> globalEntityIdsToLocalIds() {
    return new HashMap<>(globalEntityIdsToLocalIds);
  }

  public void insertIdTranslations(Map<Integer, Integer> globalToLocal) {
    int highestLocalId = 0;
    for (Map.Entry<Integer, Integer> entry : globalToLocal.entrySet()) {
      globalEntityIdsToLocalIds.put(entry.getKey(), entry.getValue());
      localEntityIdsToGlobalIds.put(entry.getValue(), entry.getKey());
      if (entry.getValue() > highestLocalId) {
        highestLocalId = entry.getValue();
      }
    }
    localEntityIdCounter = highestLocalId + 1;
  }

  public synchronized Integer newLocalIdFor(int globalEntityId) {
    int localEntityId = localEntityIdCounter++;
    int attempt = 0;
    while (localEntityIdsToGlobalIds.containsKey(localEntityId)) {
      localEntityId = localEntityIdCounter += 7;
      if (attempt++ > 50) {
        localEntityId = localEntityIdCounter += 100;
      }
    }
    globalEntityIdsForRemoval.remove(globalEntityId);
    localEntityIdsToGlobalIds.put(localEntityId, globalEntityId);
    globalEntityIdsToLocalIds.put(globalEntityId, localEntityId);
    return localEntityId;
  }

  public synchronized void markIdAsDeprecated(int globalEntityId) {
    globalEntityIdsForRemoval.add(globalEntityId);
  }

  public synchronized void removeId(int globalEntityId) {
    if (globalEntityId == -1 || globalEntityId == 0 || player == null || globalEntityId == player.getEntityId()) {
      return;
    }
    if (!globalEntityIdsForRemoval.remove(globalEntityId)) {
      return;
    }
    Integer localEntityId = globalEntityIdsToLocalIds.remove(globalEntityId);
    if (localEntityId != null) {
      localEntityIdsToGlobalIds.remove(localEntityId);
//      localEntityIdCounter = Math.min(localEntityIdCounter, localEntityId);
    }
  }

  @Deprecated
  public Map<Integer, Entity> entitiesById() {
    return entitiesById;
  }

  public Collection<Entity> entities() {
    return entities;
  }

  public Entity entityBy(int identifier) {
    return entitiesById.get(identifier);
  }

  public void destroyEntity(int entityId) {
    entitiesById.put(entityId, Entity.destroyedEntity());
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

  public void enterEntity(Entity entity) {
    entitiesById.put(entity.entityId(), entity);
    entityIds.add(entity.entityId());
    entities.add(entity);
  }

  public List<Entity> tracedEntities() {
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