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

package de.jpx3.intave.check.combat.heuristics.other;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.plib.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.packet.contract.PacketType.Client.*;

public final class PacketOrderSwingHeuristic extends ClassicHeuristic<PacketOrderSwingHeuristic.PacketOrderSwingHeuristicMeta> {
  private final IntavePlugin plugin;

  public PacketOrderSwingHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.SWING_ORDER, PacketOrderSwingHeuristicMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK, ARM_ANIMATION
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    PacketOrderSwingHeuristicMeta heuristicMeta = metaOf(player);
    heuristicMeta.swingTick = event.getPacketType() == PacketType.Play.Client.ARM_ANIMATION;
  }

  @PacketSubscription(
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void receiveUseEntity(
    User user, EntityUseReader reader
  ) {
    ProtocolMetadata protocol = user.meta().protocol();
    PacketOrderSwingHeuristicMeta heuristicMeta = metaOf(user);
    if (user.meta().abilities().ignoringMovementPackets()) {
      return;
    }
    if (reader.isAttackPacket() && protocol.emptyFlyingPacketsAreExplicitlySent() && !heuristicMeta.swingTick) {
      flag(user, "swing not correlated with attack", "version: " + protocol.versionString());
      //dmc11
      user.nerf(AttackNerfStrategy.DMG_LIGHT, "11");
    }
  }

  public static final class PacketOrderSwingHeuristicMeta extends CheckCustomMetadata {
    private boolean swingTick;
  }
}
