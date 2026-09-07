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

package de.jpx3.intave.check.combat.heuristics.testing;

import ac.intave.samples.event.ClickEvent;
import ac.intave.samples.event.PlayerMoveEvent;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.nayoro.NayoroRelay;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.nayoro.PlayerContainer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import java.util.UUID;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;

public final class TestingHeuristic extends MetaCheckPart<Heuristics, TestingHeuristic.ExampleMeta> {
  public TestingHeuristic(Heuristics parent) {
    super(parent, ExampleMeta.class);
  }

  @NayoroRelay
  public void on(PlayerContainer player, ClickEvent event) {
    ExampleMeta meta = player.meta(ExampleMeta.class);

//    player.debug("This is a test, " + meta.uniqueId);
  }

  @NayoroRelay
  public void on(PlayerContainer player, PlayerMoveEvent event) {
    ExampleMeta meta = player.meta(ExampleMeta.class);

//    player.debug("This is a test, " + meta.uniqueId);
  }

//  @NayoroRelay
//  public void on(PlayerContainer player, EntityMoveEvent event) {
//    player.debug("Entity " + event.entityId() + " moved to " + event.x() + "," + event.y() + "," + event.z());
//  }

  @PacketSubscription(
    packetsIn = {
      BLOCK_PLACE, BLOCK_DIG
    }
  )
  public void receiveInteractionPacket(PacketEvent event) {
    handleInteractionPacket(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      BLOCK_PLACE, BLOCK_DIG
    }
  )
  public void receiveInteractionPacket(Player player) {
    // PacketEvents can deliver a packet before the Bukkit player exists.
    if (player == null) {
      return;
    }
    handleInteractionPacket(player);
  }

  /** Engine independent handling; the body never reads the packet, so no view is needed. */
  private void handleInteractionPacket(Player player) {
    User user = userOf(player);
    ExampleMeta exampleMeta = metaOf(user);
  }

  public static class ExampleMeta extends CheckCustomMetadata {
    long uniqueId = UUID.randomUUID().getLeastSignificantBits();
  }
}
