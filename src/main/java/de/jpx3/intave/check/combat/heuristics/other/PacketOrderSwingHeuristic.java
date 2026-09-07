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
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.view.PacketEventsAttackView;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

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
    handleMovementPacket(
      event.getPlayer(),
      event.getPacketType() == PacketType.Play.Client.ARM_ANIMATION
    );
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK, ARM_ANIMATION
    }
  )
  public void receiveMovementPacket(Player player, PacketReceiveEvent event) {
    if (player == null) {
      return;
    }
    // PacketEvents calls the client's arm swing packet ANIMATION; ProtocolLib calls it
    // ARM_ANIMATION. Same packet, so the flag below means the same thing.
    handleMovementPacket(
      player,
      event.getPacketType()
        == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.ANIMATION
    );
  }

  /**
   * Engine independent handling: the only thing read off the packet is whether it was the swing,
   * so the discriminator is passed in as a boolean instead of the packet.
   */
  private void handleMovementPacket(Player player, boolean armAnimation) {
    PacketOrderSwingHeuristicMeta heuristicMeta = metaOf(player);
    heuristicMeta.swingTick = armAnimation;
  }

  @PacketSubscription(
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void receiveUseEntity(
    User user, EntityUseReader reader
  ) {
    handleUseEntity(user, reader.isAttackPacket());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void receiveUseEntity(PacketReceiveEvent event) {
    PacketEventsAttackView view = PacketEventsAttackView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleUseEntity(userOf(player), view.isAttackPacket());
  }

  /**
   * Engine independent handling; the only thing read off the packet is whether the interaction was
   * an attack, which both backends normalise the same way (see
   * {@link de.jpx3.intave.packet.view.AttackView}).
   */
  private void handleUseEntity(User user, boolean attackPacket) {
    ProtocolMetadata protocol = user.meta().protocol();
    PacketOrderSwingHeuristicMeta heuristicMeta = metaOf(user);
    if (user.meta().abilities().ignoringMovementPackets()) {
      return;
    }
    if (attackPacket && protocol.emptyFlyingPacketsAreExplicitlySent() && !heuristicMeta.swingTick) {
      flag(user, "swing not correlated with attack", "version: " + protocol.versionString());
      //dmc11
      user.nerf(AttackNerfStrategy.DMG_LIGHT, "11");
    }
  }

  public static final class PacketOrderSwingHeuristicMeta extends CheckCustomMetadata {
    private boolean swingTick;
  }
}
