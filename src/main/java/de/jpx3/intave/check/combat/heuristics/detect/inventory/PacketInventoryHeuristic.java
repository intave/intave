package de.jpx3.intave.check.combat.heuristics.detect.inventory;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.BURN_LONGER;

public final class PacketInventoryHeuristic extends MetaCheckPart<Heuristics, PacketInventoryHeuristic.PacketInventoryMeta> {
  private final IntavePlugin plugin;

  public PacketInventoryHeuristic(Heuristics parentCheck) {
    super(parentCheck, PacketInventoryHeuristic.PacketInventoryMeta.class);
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
      int options = SUGGEST_MINING | DELAY_128s | LIMIT_2;
      String details = "closed inventory too quickly (" + meta.inventoryTicks + ")";
      Anomaly anomaly = Anomaly.anomalyOf("131", Confidence.NONE, Anomaly.Type.KILLAURA, details, options);
      parentCheck().saveAnomaly(player, anomaly);
      //dmc9
      user.nerf(BURN_LONGER, "9");
//      user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, "9");
//      user.applyAttackNerfer(AttackNerfStrategy.CANCEL_FIRST_HIT, "9");
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
        int options = SUGGEST_MINING | DELAY_32s | SUGGEST_MINING;
        String details = "sent rotations in inventory (" + meta.rotationsInInventory + " rotations)";
        Anomaly anomaly = Anomaly.anomalyOf("132", Confidence.NONE, Anomaly.Type.KILLAURA, details, options);
        parentCheck().saveAnomaly(player, anomaly);
        //dmc10
        user.nerf(AttackNerfStrategy.HT_LIGHT, "10");
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