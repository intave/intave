package de.jpx3.intave.user;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import de.jpx3.intave.event.entity.WrappedEntity;
import de.jpx3.intave.event.transaction.TFRequest;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.RotationMathHelper;
import de.jpx3.intave.tools.annotate.DispatchCrossCall;
import de.jpx3.intave.tools.annotate.Relocate;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

@Relocate
public final class UserMetaConnectionData {
  private final Player player;
  private final Map<Short, TFRequest<?>> transactionShortMap = Maps.newConcurrentMap();
  private final Map<Long, TFRequest<?>> transactionGlobalKeyMap = Maps.newConcurrentMap();
  private final Map<Long, Queue<TFRequest<?>>> transactionOptionalAppendixMap = Maps.newConcurrentMap();
  private final Map<Integer, WrappedEntity> synchronizedEntityMap = Maps.newConcurrentMap();
  private final Map<Long, Long> remainingPingPacketTimestamps = Maps.newConcurrentMap();
  private final List<Long> latencyDifferenceBalance = Lists.newCopyOnWriteArrayList();
  public final ReentrantLock transactionLock = new ReentrantLock(false);
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

  // Lag identification
  private long lastMovementTimestamps;
  private final List<Long> movementLagSpikeHistory = new ArrayList<>();
  
  public UserMetaConnectionData(Player player) {
    this.player = player;
  }

  @DispatchCrossCall
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
    return RotationMathHelper.averageOf(movementLagSpikeHistory);
  }

  public Map<Short, TFRequest<?>> transactionShortKeyMap() {
    return transactionShortMap;
  }

  public Map<Long, TFRequest<?>> transactionGlobalKeyMap() {
    return transactionGlobalKeyMap;
  }

  public Map<Long, Queue<TFRequest<?>>> transactionAppendixMap() {
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