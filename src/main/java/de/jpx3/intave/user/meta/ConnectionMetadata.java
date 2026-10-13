/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.user.meta;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.executor.RateLimiter;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.math.Occurrences;
import de.jpx3.intave.module.feedback.DelayedPacket;
import de.jpx3.intave.module.feedback.FeedbackQueue;
import de.jpx3.intave.module.feedback.FeedbackRequest;
import de.jpx3.intave.module.feedback.PendingCountingFeedbackObserver;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.plib.PacketSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public final class ConnectionMetadata {
  private final Player player;
  private final FeedbackQueue feedbackQueue = new FeedbackQueue();
  public final ReentrantLock feedbackLock = new ReentrantLock();
  private final Map<Long, Queue<FeedbackRequest<?>>> transactionOptionalAppendMap = Maps.newConcurrentMap();
  private final Map<Integer, Entity> entitiesById = Maps.newConcurrentMap();
  private final Map<Integer, Integer> entityVehicles = Maps.newConcurrentMap();
  private final Map<Integer, Set<Integer>> entityMounts = Maps.newConcurrentMap();
  private static final long LOADED_CLIENT_CHUNK = 0L;
  private final Map<Long, Long> clientChunkStates = Maps.newConcurrentMap();
  private final AtomicLong clientChunkSequence = new AtomicLong();
  private final ClientScoreboard clientScoreboard = new ClientScoreboard();

  private final Set<Integer> entityIds = new HashSet<>();
  private final List<Entity> synchronizedEntities = Lists.newCopyOnWriteArrayList();
  public PendingCountingFeedbackObserver pendingBlockUpdates;
  private List<Entity> tickedEntities = new CopyOnWriteArrayList<>();
  private final Map<Long, Deque<Long>> remainingPingPacketTimestamps = new ConcurrentHashMap<>();
  private final List<Long> latencyDifferenceBalance = Lists.newCopyOnWriteArrayList();

  // not used
  private final Map<Integer, Integer> localEntityIdsToGlobalIds = Maps.newConcurrentMap();
  private final Map<Integer, Integer> globalEntityIdsToLocalIds = Maps.newConcurrentMap();
  private final Set<Integer> globalEntityIdsForRemoval = new HashSet<>();

  public final Queue<Runnable> nextFeedbackSubscribers = new LinkedList<>();

  public final Map<Integer, Integer> duplicationOwners = new HashMap<>();
  public final Map<Integer, DecoySide> decoySides = new HashMap<>();
  public final Set<Integer> duplicatedEntityIds = new HashSet<>();
  public final Set<Integer> shouldNotBeAttacked = new HashSet<>();
  @Deprecated
  public boolean markAttackInvalid;

  public boolean debugEntityTracing = false;

  public RateLimiter refreshBlockRatelimit = new RateLimiter(400, 2, TimeUnit.SECONDS);

  public final Random feedbackUserKeyRandom = new Random();
  public int lastFeedbackUserKey = Integer.MIN_VALUE;
  public int generatorRunningNum = 0;

  public int simulatedBlockAckNum;

  public int windowClickId;
  public int transactionKeepAliveInvalidOrderVL;

  public enum DecoySide {
    FIRST_IS_DECOY,
    SECOND_IS_DECOY,
  }

  public enum ClientChunkState {
    UNLOADED,
    PENDING,
    LOADED,
  }

  private int localEntityIdCounter = 1;
  public long lastCCCInfoMessageSent = 0;
  public boolean sendAsyncMessage = false;
  public boolean eligibleForTransactionTimeout = false;

  private final Deque<Object> bufferEnqueue = new ArrayDeque<>(8500);
  private final DelayQueue<DelayedPacket> delayQueue = new DelayQueue<>();
  public long lastBufferNotification = 0;
  public long lastDelayNotification = 0;
  public long lastDelaySlot = 0;
  public long lastBufferEnqueue = 0;
  public long timestampRequiredForAttack = 0;
  public boolean ignorePacketEnqueue;
  public long delayedPackets = 0;
  public long lastDelayRequest = 0;
  public long blinkDeactivated = 0;
  public long lastRespawn = 0;
  public long firstEnqueue = 0;
  public boolean lastBlinkState = false;

  // Client Synchronization
  public int latency;
  public long lastKeepAliveDifference;
  public int latencyJitter;
  public long transactionNumCounter = 0;
  public long lastReceivedTransactionNum = -1;
  public long lastSynchronization = System.nanoTime();
  public long transactionPacketCounter;
  public long transactionPacketCounterReset;

  public long feedbackFaults = 0;

  public int nextWindowOpenSlots = 0;
  public boolean assumeWindowOpen = false;
  public int assumedWindowId = 0;

  // Lag identification
  private long lastMovementTimestamps;
  private final List<Long> movementLagSpikeHistory = new ArrayList<>();

  private static final long DELAY_PURGE_INTERVAL = 1000 * 60;
  public final Occurrences<Integer> attackDelays = new Occurrences<>(DELAY_PURGE_INTERVAL);
  public final Occurrences<Integer> feedbackDelays = new Occurrences<>(DELAY_PURGE_INTERVAL);

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
	  return MathHelper.averageOf(data);
  }

  private long transactionSum = 0;
  private long transactionNum = 0;

  private long shortTransactionSum = 0;
  private long shortTransactionNum = 0;
  private long lastShortReset = System.currentTimeMillis();

  public void receivedTransactionAfter(long milliseconds) {
    long cappedMillis = Math.min(milliseconds, 1000);
    transactionSum += cappedMillis;
    transactionNum++;
    shortTransactionSum += cappedMillis;
    shortTransactionNum++;
    if (transactionNum > 4096) {
      transactionSum /= 2;
      transactionNum /= 2;
    }
    if (System.currentTimeMillis() - lastShortReset > 4000) {
      shortTransactionSum /= 2;
      shortTransactionNum /= 2;
      lastShortReset = System.currentTimeMillis();
    }
    feedbackDelays.occurred(Math.min(40, (int) milliseconds / 50));
    if (IntaveControl.LATENCY_PING_AS_XP_LEVEL && transactionNum % 10 == 0) {
      sendPacketWithExperience(player, (int) ((int) transactionPingAverage() * 1000 + shortTransactionPingAverage()));
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

  public long shortTransactionPingAverage() {
    return shortTransactionNum == 0 ? 0 : shortTransactionSum / shortTransactionNum;
  }

  public FeedbackQueue feedbackQueue() {
    return feedbackQueue;
  }

  public ClientScoreboard clientScoreboard() {
    return clientScoreboard;
  }

  public Map<Long, Queue<FeedbackRequest<?>>> transactionAppendMap() {
    return transactionOptionalAppendMap;
  }

  /**
   * Marks a chunk packet as sent and returns the action that confirms the chunk after feedback.
   * Replaced, unloaded, and world-invalidated loads cannot be confirmed by an older callback.
   */
  public Runnable pendingClientChunkLoad(int chunkX, int chunkZ) {
    long chunkKey = clientChunkKey(chunkX, chunkZ);
    long sequence;
    do {
      sequence = clientChunkSequence.incrementAndGet();
    } while (sequence == LOADED_CLIENT_CHUNK);
    clientChunkStates.put(chunkKey, sequence);
    long expectedSequence = sequence;
    return () -> clientChunkStates.replace(chunkKey, expectedSequence, LOADED_CLIENT_CHUNK);
  }

  public void unloadClientChunk(int chunkX, int chunkZ) {
    clientChunkStates.remove(clientChunkKey(chunkX, chunkZ));
  }

  public void clearClientChunks() {
    clientChunkStates.clear();
  }

  public boolean hasClientChunk(int chunkX, int chunkZ) {
    return clientChunkStates.getOrDefault(clientChunkKey(chunkX, chunkZ), -1L) == LOADED_CLIENT_CHUNK;
  }

  public ClientChunkState clientChunkState(int chunkX, int chunkZ) {
    Long state = clientChunkStates.get(clientChunkKey(chunkX, chunkZ));
    if (state == null) {
      return ClientChunkState.UNLOADED;
    }
    return state == LOADED_CLIENT_CHUNK ? ClientChunkState.LOADED : ClientChunkState.PENDING;
  }

  /**
   * Returns a snapshot of feedback-confirmed chunks using Minecraft's packed chunk coordinate format.
   */
  public Set<Long> clientChunks() {
    Set<Long> chunks = new HashSet<>();
    clientChunkStates.forEach((chunk, state) -> {
      if (state == LOADED_CLIENT_CHUNK) {
        chunks.add(chunk);
      }
    });
    return chunks;
  }

  public static long clientChunkKey(int chunkX, int chunkZ) {
    return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
  }

  public static int clientChunkX(long chunkKey) {
    return (int) chunkKey;
  }

  public static int clientChunkZ(long chunkKey) {
    return (int) (chunkKey >> 32);
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

  public void noteMount(int entityId, int vehicleId) {
    Integer previousVehicle = entityVehicles.get(entityId);
    if (previousVehicle != null && previousVehicle != vehicleId) {
      noteDismount(entityId);
    }
    entityVehicles.put(entityId, vehicleId);
    entityMounts.computeIfAbsent(vehicleId, k -> new HashSet<>()).add(entityId);
  }

  public void noteDismount(int entityId) {
    Integer vehicleId = entityVehicles.remove(entityId);
    if (vehicleId != null) {
      Set<Integer> mounts = entityMounts.get(vehicleId);
      if (mounts != null) {
        mounts.remove(entityId);
        if (mounts.isEmpty()) {
          entityMounts.remove(vehicleId);
        }
      }
    }
  }

  public Integer vehicleOf(int entityId) {
    return entityVehicles.get(entityId);
  }

  public List<Integer> sittingOn(int entityId) {
    return entityMounts.get(entityId) == null ? Collections.emptyList() : new ArrayList<>(entityMounts.get(entityId));
  }

  @Deprecated
  public Collection<Entity> entities() {
    return entitiesById.values();
  }

  @Nullable
  public Entity entityBy(int identifier) {
    return entitiesById.get(identifier);
  }

  @Nullable
  public Entity entityBy(Integer identifier) {
    return entitiesById.get(identifier);
  }

  public void enterEntity(Entity entity) {
    entitiesById.put(entity.entityId(), entity);
    entityIds.add(entity.entityId());
//    entities.add(entity);
//    tickedEntities.add(entity);
  }

  public Entity markForDeletion(int entityId) {
    Entity old = entitiesById.put(entityId, Entity.destroyedEntity());
    entityIds.remove(entityId);
    // we will not override the entity collection, as it would require a lot of performance and seems quite redundant in the first place
//    for (int i = 0, entitiesSize = entities.size(); i < entitiesSize; i++) {
//      EntityShade entity = entities.get(i);
//      if (entity.entityId() == entityId) {
//        entities.set(i, EntityShade.destroyedEntity());
//      }
//    }
    // using removeIf requires the least amount of locking and array modifications for CopyOnWriteArrayLists
//    entities.removeIf(entity -> entity.entityId() == entityId);
    return old;
  }

  public void removeEntityIfMarked(int entityId) {
    if (entitiesById.get(entityId) instanceof Entity.Destroyed) {
      entitiesById.remove(entityId);
    }
  }

  public void removeEntitiesIfMarked(int[] entityIds) {
    for (int entityId : entityIds) {
      removeEntityIfMarked(entityId);
    }
  }

  public void queueToNextTransaction(Runnable runnable) {
    nextFeedbackSubscribers.add(runnable);
  }

  public DelayQueue<DelayedPacket> delayedPackets() {
    return delayQueue;
  }

  public List<Entity> tracedEntities() {
    return synchronizedEntities;
  }

  public List<Entity> tickedEntities() {
    return tickedEntities;
  }

  public void setTickedEntities(List<Entity> ticked) {
    this.tickedEntities = ticked;
  }

  public void addPendingKeepAlive(long identifier, long sentAt) {
    remainingPingPacketTimestamps.compute(identifier, (key, timestamps) -> {
      Deque<Long> pending = timestamps;
      if (pending == null) {
        pending = new ConcurrentLinkedDeque<>();
      }
      pending.addLast(sentAt);
      return pending;
    });
  }

  public @Nullable Long pollPendingKeepAlive(long identifier) {
    Long[] result = new Long[1];
    remainingPingPacketTimestamps.computeIfPresent(identifier, (key, timestamps) -> {
      result[0] = timestamps.pollFirst();
      return timestamps.isEmpty() ? null : timestamps;
    });
    return result[0];
  }

  public void discardPendingKeepAlivesBefore(long cutoff) {
    for (Long identifier : remainingPingPacketTimestamps.keySet()) {
      remainingPingPacketTimestamps.computeIfPresent(identifier, (key, timestamps) -> {
        Long first;
        while ((first = timestamps.peekFirst()) != null && first < cutoff) {
          timestamps.pollFirst();
        }
        return timestamps.isEmpty() ? null : timestamps;
      });
    }
  }

  public Set<Long> pendingKeepAliveIdentifiers() {
    return new HashSet<>(remainingPingPacketTimestamps.keySet());
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
