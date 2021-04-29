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
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.punishment.AttackNerfStrategy;
import de.jpx3.intave.user.*;
import org.bukkit.entity.Player;

import static de.jpx3.intave.detect.checks.combat.heuristics.Anomaly.AnomalyOption.*;

public final class PacketInventoryHeuristic extends IntaveMetaCheckPart<Heuristics, PacketInventoryHeuristic.PacketInventoryMeta> {
  private final IntavePlugin plugin;

  public PacketInventoryHeuristic(Heuristics parentCheck) {
    super(parentCheck, PacketInventoryHeuristic.PacketInventoryMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "CLIENT_COMMAND"),
    }
  )
  public void receiveInventoryOpen(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    EnumWrappers.ClientCommand clientCommand = event.getPacket().getClientCommands().read(0);
    if (clientCommand == EnumWrappers.ClientCommand.OPEN_INVENTORY_ACHIEVEMENT) {
      PacketInventoryMeta meta = metaOf(user);
      meta.performedInventoryOpenOperation = true;
      meta.inventoryTicks = 0;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "CLOSE_WINDOW"),
    }
  )
  public void receiveInventoryClose(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketInventoryMeta meta = metaOf(user);
    UserMetaClientData clientData = user.meta().clientData();

    if (clientData.flyingPacketStream() && meta.inventoryTicks <= 1 && meta.performedInventoryOpenOperation) {
      int options = SUGGEST_MINING | DELAY_128s | SUGGEST_MINING;
      String details = "closed inventory too quickly (" + meta.inventoryTicks + ")";
      Anomaly anomaly = Anomaly.anomalyOf("131", Confidence.PROBABLE, Anomaly.Type.KILLAURA, details, options);
      parentCheck().saveAnomaly(player, anomaly);
      user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION")
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

    if (inventoryOpen && hasRotation && movementData.lastTeleport > 20) {
      if (meta.rotationsInInventory++ > 1) {
        int options = SUGGEST_MINING | DELAY_32s | SUGGEST_MINING;
        String details = "sent rotations in inventory (" + meta.rotationsInInventory + " rotations)";
        Anomaly anomaly = Anomaly.anomalyOf("132", Confidence.NONE, Anomaly.Type.KILLAURA, details, options);
        parentCheck().saveAnomaly(player, anomaly);
        user.applyAttackNerfer(AttackNerfStrategy.HT_LIGHT);
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