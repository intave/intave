package de.jpx3.intave.user.meta;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.event.feedback.Request;
import de.jpx3.intave.module.tracker.entity.WrappedEntity;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.RotationUtilities;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

@Relocate
public final class ConnectionMetadata {
  private final Player player;
  private final Map<Short, Request<?>> transactionShortMap = Maps.newConcurrentMap();
  private final Map<Long, Request<?>> transactionGlobalKeyMap = Maps.newConcurrentMap();
  private final Map<Long, Queue<Request<?>>> transactionOptionalAppendixMap = Maps.newConcurrentMap();
  private final Map<Integer, WrappedEntity> synchronizedEntityMap = Maps.newConcurrentMap();
  private final Map<Long, Long> remainingPingPacketTimestamps = Maps.newConcurrentMap();
  private final List<Long> latencyDifferenceBalance = Lists.newCopyOnWriteArrayList();
  public long lastCCCInfoMessageSent = 0;
  public boolean sendAsyncMessage = false;

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
    return RotationUtilities.averageOf(movementLagSpikeHistory);
  }

  public Map<Short, Request<?>> transactionShortKeyMap() {
    return transactionShortMap;
  }

  public Map<Long, Request<?>> transactionGlobalKeyMap() {
    return transactionGlobalKeyMap;
  }

  public Map<Long, Queue<Request<?>>> transactionAppendixMap() {
    return transactionOptionalAppendixMap;
  }

  public Map<Integer, WrappedEntity> synchronizedEntityMap() {
    return synchronizedEntityMap;
  }

  public Map<Long, Long> remainingPingPacketTimestamps() {
    return remainingPingPacketTimestamps;
  }

  public List<Long> latencyDifferenceBalance() {
    return latencyDifferenceBalance;
  }
}