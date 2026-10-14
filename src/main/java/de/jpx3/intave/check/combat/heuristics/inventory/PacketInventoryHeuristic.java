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

package de.jpx3.intave.check.combat.heuristics.inventory;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.reader.ClientCommandReader;
import de.jpx3.intave.share.ClientCommand;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.BURN_LONGER;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.DMG_HIGH;

public final class PacketInventoryHeuristic extends ClassicHeuristic<PacketInventoryHeuristic.PacketInventoryMeta> {

	public PacketInventoryHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.INVENTORY_ROTATIONS, PacketInventoryHeuristic.PacketInventoryMeta.class);
	}

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      CLIENT_COMMAND
    }
  )
  public void receiveInventoryOpen(Player player, ClientCommandReader reader) {
    User user = userOf(player);
    if (reader.command() == ClientCommand.OPEN_INVENTORY) {
      PacketInventoryMeta meta = metaOf(user);
      meta.performedInventoryOpenOperation = true;
      meta.inventoryTicks = 0;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      CLOSE_WINDOW
    }
  )
  public void receiveInventoryClose(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketInventoryMeta meta = metaOf(user);
    ProtocolMetadata clientData = user.meta().protocol();
    AbilityMetadata abilityData = user.meta().abilities();

    if (abilityData.ignoringMovementPackets()) {
      return;
    }

    if (clientData.emptyFlyingPacketsAreExplicitlySent() && meta.inventoryTicks == 0 && meta.performedInventoryOpenOperation) {
      flag(user, "closed inventory too quickly", meta.inventoryTicks + " ticks");
      user.nerf(BURN_LONGER, nerfId);
      user.nerf(DMG_HIGH, nerfId);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION, POSITION_LOOK, FLYING, LOOK
    }
  )
  public void receiveMovement(User user, PlayerMoveReader reader) {
    PacketInventoryMeta meta = metaOf(user);
    boolean hasRotation = reader.hasRotation();

    InventoryMetadata inventoryData = user.meta().inventory();
    SimulationEnvironment movementData = user.meta().movement();
    ProtocolMetadata clientData = user.meta().protocol();

    if (!clientData.emptyFlyingPacketsAreExplicitlySent() || movementData.isInVehicle()) {
      return;
    }

    boolean inventoryOpen = inventoryData.inventoryOpen();

    if (!inventoryOpen) {
      meta.performedInventoryOpenOperation = false;
    }

    if (inventoryOpen && hasRotation && movementData.ticksPast(TELEPORT) > 20 && !user.player().isInsideVehicle()) {
      if (meta.rotationsInInventory++ > 1) {
        flag(user, "sent rotations while an inventory was open", meta.rotationsInInventory + " rotations");
        user.nerf(AttackNerfStrategy.HT_LIGHT, nerfId);
      }
    }

    if (!inventoryOpen) {
      meta.reset();
    }

    if (meta.performedInventoryOpenOperation) {
      meta.inventoryTicks++;
    } else {
      meta.inventoryTicks = 0;
    }
  }

  public static final class PacketInventoryMeta extends CheckCustomMetadata {
    private int rotationsInInventory;
    private int inventoryTicks;
    private boolean performedInventoryOpenOperation;

    private void reset() {
      rotationsInInventory = 0;
    }
  }
}
