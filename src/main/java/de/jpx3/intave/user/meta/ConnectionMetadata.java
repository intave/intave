package de.jpx3.intave.user.meta;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.event.AccessHelper;
import de.jpx3.intave.module.feedback.FeedbackRequest;
import de.jpx3.intave.module.tracker.entity.WrappedEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

@Relocate
public final class ConnectionMetadata {
  private final Player player;
  private final Map<Short, FeedbackRequest<?>> transactionShortMap = Maps.newConcurrentMap();
  private final Map<Long, FeedbackRequest<?>> transactionGlobalKeyMap = Maps.newConcurrentMap();
  private final Map<Long, Queue<FeedbackRequest<?>>> transactionOptionalAppendixMap = Maps.newConcurrentMap();
  private final Map<Integer, WrappedEntity> entities = Maps.newConcurrentMap();
  private final List<WrappedEntity> synchronizedEntities = Lists.newCopyOnWriteArrayList();
  private final Map<Long, Long> remainingPingPacketTimestamps = Maps.newConcurrentMap();
  private final List<Long> latencyDifferenceBalance = Lists.newCopyOnWriteArrayList();
  public long lastCCCInfoMessageSent = 0;
  public boolean sendAsyncMessage = false;
  public boolean eligibleForTransactionTimeout = false;

  // Client Synchronization
  public int latency;
  public long lastKeepAliveDifference;
  public int latencyJitter;
  public short transactionCounter = Short.MIN_VALUE;
  public long transactionNumCounter = 0;
  public long lastReceivedTransactionNum = -1;
  public long lastSynchronization = AccessHelper.now();
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
    long now = AccessHelper.now();
    if (this.lastMovementTimestamps != 0) {
      long difference = now - lastMovementTimestamps;
      movementLagSpikeHistory.add(difference);
      if (movementLagSpikeHistory.size() > 3) {
        movementLagSpikeHistory.remove(0);
      }
    }
    this.lastMovementTimestamps = now;
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

  public Map<Short, FeedbackRequest<?>> transactionShortKeyMap() {
    return transactionShortMap;
  }

  public Map<Long, FeedbackRequest<?>> transactionGlobalKeyMap() {
    return transactionGlobalKeyMap;
  }

  public Map<Long, Queue<FeedbackRequest<?>>> transactionAppendMap() {
    return transactionOptionalAppendixMap;
  }

  public Map<Integer, WrappedEntity> entities() {
    return entities;
  }

  public List<WrappedEntity> tracedEntities() {
    return synchronizedEntities;
  }

  public Map<Long, Long> pingPackets() {
    return remainingPingPacketTimestamps;
  }

  public List<Long> latencyDifferenceBalance() {
    return latencyDifferenceBalance;
  }
}