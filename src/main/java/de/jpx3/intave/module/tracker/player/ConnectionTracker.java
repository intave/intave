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

package de.jpx3.intave.module.tracker.player;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packetperfect.OnReceive;
import de.jpx3.intave.module.linker.packetperfect.OnSend;
import de.jpx3.intave.packet.contract.immutable.KeepAlivePacket;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import java.util.List;

import static de.jpx3.intave.packet.ListenerPriority.LAST;
import static de.jpx3.intave.packet.ListenerStage.AFTER;

public final class ConnectionTracker extends Module {
  private static final long MAX_PENDING_AGE = 1000 * 30;

  @OnSend(priority = LAST, stage = AFTER)
  public void processOutgoingPingPackets(User user, Cancellable cancellable, KeepAlivePacket packet) {
    if (cancellable.isCancelled()) {
      return;
    }
    long id = packet.id();
    long now = System.currentTimeMillis();
    ConnectionMetadata connection = user.meta().connection();
    connection.discardPendingKeepAlivesBefore(now - MAX_PENDING_AGE);
    connection.addPendingKeepAlive(id, now);
  }

  @OnReceive(priority = LAST, stage = AFTER)
  public void processIncomingPingPackets(User user, Cancellable cancellable, KeepAlivePacket packet) {
    if (cancellable.isCancelled()) {
      return;
    }
    Player player = user.player();
    ConnectionMetadata synchronizeData = user.meta().connection();
    long id = packet.id();
    if (id == 0) {
      return;
    }
    Long timeSent = synchronizeData.pollPendingKeepAlive(id);
    if (timeSent == null) {
      if (!user.justJoined()) {
        IntaveLogger.logger().info(player.getName() + " sent untracked keep-alive id " + id + ", but expected one of " + synchronizeData.pendingKeepAliveIdentifiers());
      }
      return;
    }
    List<Long> differenceBalance = synchronizeData.latencyDifferenceBalance();
    long difference = MathHelper.minmax(0, System.currentTimeMillis() - timeSent, 1000);
    synchronizeData.latency = (int) (((synchronizeData.latency * 3d) + difference) / 4d);
    long pingChange = Math.abs(difference - synchronizeData.lastKeepAliveDifference);
    int size = 8;
    boolean enoughPingDataAvailable = differenceBalance.size() >= size;
    if (enoughPingDataAvailable) {
      differenceBalance.remove(0);
    }
    differenceBalance.add(pingChange);
    if (enoughPingDataAvailable) {
      long sum = 0;
      long count = 0;
      for (Long value : differenceBalance) {
        long l = value;
        sum += l;
        count++;
      }
      user.meta().connection().latencyJitter =
        (int) (count > 0 ? (double) sum / count : 0d);
    }
    plugin.accessService()
      .playerAccessor()
      .netStatisticsAccessor()
      .pushPingJitterUpdate(player, synchronizeData.latency, (int) pingChange);
    synchronizeData.lastKeepAliveDifference = difference;
  }
}
