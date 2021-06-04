package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.violation.AttackNerfStrategy;
import de.jpx3.intave.user.*;
import org.bukkit.entity.Player;

import static de.jpx3.intave.detect.checks.combat.heuristics.Anomaly.AnomalyOption.*;
import static de.jpx3.intave.event.packet.PacketId.Client.*;

public final class PacketInventoryHeuristic extends IntaveMetaCheckPart<Heuristics, PacketInventoryHeuristic.PacketInventoryMeta> {
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
    UserMetaClientData clientData = user.meta().clientData();
    UserMetaAbilityData abilityData = user.meta().abilityData();

    if (abilityData.ignoringMovementPackets()) {
      return;
    }

    if (clientData.flyingPacketStream() && meta.inventoryTicks == 0 && meta.performedInventoryOpenOperation) {
      int options = SUGGEST_MINING | DELAY_128s | LIMIT_2;
      String details = "closed inventory too quickly (" + meta.inventoryTicks + ")";
      Anomaly anomaly = Anomaly.anomalyOf("131", Confidence.LIKELY, Anomaly.Type.KILLAURA, details, options);
      parentCheck().saveAnomaly(player, anomaly);
      //dmc9
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

    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    UserMetaMovementData movementData = user.meta().movementData();
    UserMetaClientData clientData = user.meta().clientData();

    if (!clientData.flyingPacketStream() || movementData.inVehicle()) {
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
        user.applyAttackNerfer(AttackNerfStrategy.HT_LIGHT, "10");
      }
    }

    if (!inventoryOpen) {
      meta.reset();
    }

    if (meta.performedInventoryOpenOperation) {
      meta.inventoryTicks++;
    } else{
      meta.inventoryTicks = 0;
    }
  }

  public final static class PacketInventoryMeta extends UserCustomCheckMeta {
    private int rotationsInInventory;
    private int inventoryTicks;
    private boolean performedInventoryOpenOperation;

    private void reset() {
      rotationsInInventory = 0;
    }
  }
}