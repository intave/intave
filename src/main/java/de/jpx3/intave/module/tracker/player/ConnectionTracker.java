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

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.PrioritySlot;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.bukkit.entity.Player;

import java.util.List;

public final class ConnectionTracker extends Module {
  private static final long MAX_PENDING_AGE = 1000 * 30;

  @PacketSubscription(
    priority = ListenerPriority.MONITOR,
    prioritySlot = PrioritySlot.EXTERNAL,
    packetsOut = {
      PacketId.Server.KEEP_ALIVE
    },
    ignoreCancelled = false
  )
  public void processOutgoingPingPackets(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    long id;
    if (packet.getLongs().size() > 0) {
      id = packet.getLongs().read(0);
    } else {
      id = packet.getIntegers().read(0);
    }
    handleOutgoingPingPacket(UserRepository.userOf(player), id);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.MONITOR,
    prioritySlot = PrioritySlot.EXTERNAL,
    packetsOut = {
      PacketId.Server.KEEP_ALIVE
    },
    ignoreCancelled = false
  )
  public void processOutgoingPingPackets(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    // PacketEvents already normalises the pre 1.12.2 int keep alive id into a long.
    handleOutgoingPingPacket(
      UserRepository.userOf(player),
      new WrapperPlayServerKeepAlive(event).getId()
    );
  }

  /** Engine independent outbound keep-alive handling; only the keep-alive id is read. */
  private void handleOutgoingPingPacket(User user, long id) {
    long now = System.currentTimeMillis();
    ConnectionMetadata connection = user.meta().connection();
    connection.discardPendingKeepAlivesBefore(now - MAX_PENDING_AGE);
    connection.addPendingKeepAlive(id, now);
  }

  @PacketSubscription(
    priority = ListenerPriority.MONITOR,
    prioritySlot = PrioritySlot.EXTERNAL,
    packetsIn = {
      PacketId.Client.KEEP_ALIVE
    },
    ignoreCancelled = false
  )
  public void processIncomingPingPackets(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    long id;
    if (packet.getLongs().size() > 0) {
      id = packet.getLongs().read(0);
    } else {
      id = Long.valueOf(packet.getIntegers().read(0));
    }
    handleIncomingPingPacket(player, UserRepository.userOf(player), id);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.MONITOR,
    prioritySlot = PrioritySlot.EXTERNAL,
    packetsIn = {
      PacketId.Client.KEEP_ALIVE
    },
    ignoreCancelled = false
  )
  public void processIncomingPingPackets(PacketReceiveEvent event, Player player) {
    if (player == null) {
      return;
    }
    // PacketEvents already normalises the pre 1.12.2 int keep alive id into a long.
    handleIncomingPingPacket(
      player,
      UserRepository.userOf(player),
      new WrapperPlayClientKeepAlive(event).getId()
    );
  }

  /** Engine independent inbound keep-alive handling; only the keep-alive id is read. */
  private void handleIncomingPingPacket(Player player, User user, long id) {
    ConnectionMetadata synchronizeData = user.meta().connection();
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
