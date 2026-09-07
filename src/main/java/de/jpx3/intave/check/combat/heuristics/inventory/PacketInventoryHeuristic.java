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

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClientStatus;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.view.PacketEventsMovementView;
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
  public void receiveInventoryOpen(PacketEvent event) {
    EnumWrappers.ClientCommand clientCommand = event.getPacket().getClientCommands().read(0);
    handleInventoryOpen(
      event.getPlayer(),
      clientCommand == EnumWrappers.ClientCommand.OPEN_INVENTORY_ACHIEVEMENT
    );
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.LOW,
    packetsIn = {
      CLIENT_COMMAND
    }
  )
  public void receiveInventoryOpen(PacketReceiveEvent event, Player player) {
    // PacketEvents can deliver a packet before the Bukkit player exists.
    if (player == null) {
      return;
    }
    // PacketEvents calls the client command packet CLIENT_STATUS; same packet, same action ids.
    handleInventoryOpen(
      player,
      new WrapperPlayClientClientStatus(event).getAction()
        == WrapperPlayClientClientStatus.Action.OPEN_INVENTORY_ACHIEVEMENT
    );
  }

  /**
   * Engine independent handling; the only thing read off the packet is whether it carried the
   * "open inventory" command, so that is passed in rather than the packet.
   */
  private void handleInventoryOpen(Player player, boolean inventoryOpenCommand) {
    User user = userOf(player);
    if (inventoryOpenCommand) {
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
    handleInventoryClose(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.LOW,
    packetsIn = {
      CLOSE_WINDOW
    }
  )
  public void receiveInventoryClose(Player player) {
    if (player == null) {
      return;
    }
    handleInventoryClose(player);
  }

  /** Engine independent handling; the body only reads Intave's own metadata, not the packet. */
  private void handleInventoryClose(Player player) {
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
  public void receiveMovement(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    // Left byte for byte as it was: this reads boolean index 2 directly rather than going through
    // PlayerMoveReader.hasRotation(), which shifts that index on 1.21.3+.
    boolean hasRotation = packet.getBooleans().read(2);
    handleMovement(event.getPlayer(), hasRotation);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION, POSITION_LOOK, FLYING, LOOK
    }
  )
  public void receiveMovement(PacketReceiveEvent event) {
    PacketEventsMovementView view = PacketEventsMovementView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleMovement(player, view.hasRotation());
  }

  /**
   * Engine independent handling; the only thing read off the packet is whether it carried a
   * rotation, so that is passed in rather than the packet.
   */
  private void handleMovement(Player player, boolean hasRotation) {
    User user = userOf(player);
    PacketInventoryMeta meta = metaOf(user);

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

    if (inventoryOpen && hasRotation && movementData.ticksPast(TELEPORT) > 20 && !player.isInsideVehicle()) {
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
