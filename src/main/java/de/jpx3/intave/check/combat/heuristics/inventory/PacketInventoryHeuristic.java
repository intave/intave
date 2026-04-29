package de.jpx3.intave.check.combat.heuristics.inventory;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.BURN_LONGER;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.DMG_HIGH;

public final class PacketInventoryHeuristic extends ClassicHeuristic<PacketInventoryHeuristic.PacketInventoryMeta> {
  private final IntavePlugin plugin;

  public PacketInventoryHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.INVENTORY_ROTATIONS, PacketInventoryHeuristic.PacketInventoryMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      CLIENT_COMMAND
    }
  )
  public void receiveInventoryOpen(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    EnumWrappers.ClientCommand clientCommand = event.getPacket().getClientCommands().read(0);
    if (clientCommand == EnumWrappers.ClientCommand.OPEN_INVENTORY_ACHIEVEMENT) {
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

    if (clientData.flyingPacketsAreSent() && meta.inventoryTicks == 0 && meta.performedInventoryOpenOperation) {
      flag(player, "closed inventory too quickly (" + meta.inventoryTicks + ")");
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
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketInventoryMeta meta = metaOf(user);
    PacketContainer packet = event.getPacket();
    boolean hasRotation = packet.getBooleans().read(2);

    InventoryMetadata inventoryData = user.meta().inventory();
    MovementMetadata movementData = user.meta().movement();
    ProtocolMetadata clientData = user.meta().protocol();

    if (!clientData.flyingPacketsAreSent() || movementData.isInVehicle()) {
      return;
    }

    boolean inventoryOpen = inventoryData.inventoryOpen();

    if (!inventoryOpen) {
      meta.performedInventoryOpenOperation = false;
    }

    if (inventoryOpen && hasRotation && movementData.lastTeleport > 20 && !player.isInsideVehicle()) {
      if (meta.rotationsInInventory++ > 1) {
        flag(player, "sent rotations in inventory (" + meta.rotationsInInventory + " rotations)");
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