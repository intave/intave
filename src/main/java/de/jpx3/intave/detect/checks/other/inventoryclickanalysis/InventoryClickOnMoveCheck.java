package de.jpx3.intave.detect.checks.other.inventoryclickanalysis;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.CheckPart;
import de.jpx3.intave.detect.checks.movement.physics.Simulators;
import de.jpx3.intave.detect.checks.other.InventoryClickAnalysis;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.violation.Violation;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMeta;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class InventoryClickOnMoveCheck extends CheckPart<InventoryClickAnalysis> {
  private final IntavePlugin plugin;

  public InventoryClickOnMoveCheck(InventoryClickAnalysis parentCheck) {
    super(parentCheck);
    plugin = IntavePlugin.singletonInstance();
  }

  @BukkitEventSubscription
  public void receiveWindowClick(InventoryClickEvent event) {
    HumanEntity whoClicked = event.getWhoClicked();
    if (!(whoClicked instanceof Player)) {
      return;
    }
    Player player = ((Player) whoClicked).getPlayer();
    User user = userOf(player);
    UserMeta meta = user.meta();

    ClickType click = event.getClick();
    if (click == ClickType.CREATIVE) {
      return;
    }

    UserMetaMovementData movementData = meta.movementData();
    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;

    if (movementData.simulator() == Simulators.ELYTRA) {
      return;
    }

    // Be more lenient when a flying packet was sent
    if (movementData.inWeb || movementData.recentlyEncounteredFlyingPacket(2)) {
      return;
    }

    double distanceMoved = Math.hypot(movementData.motionX(), movementData.motionZ());
    if ((keyForward != 0 || keyStrafe != 0) && distanceMoved > 0.1) {
      String message = "performed inventory-click whilst walking";
      Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
        .forPlayer(player)
        .withMessage(message)
        .withVL(0)
        .build();
      plugin.violationProcessor().processViolation(violation);
      Synchronizer.synchronize(player::closeInventory);
      event.setCancelled(true);
    }
  }
}