package de.jpx3.intave.detect.checks.other.inventoryclickanalysis;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveCheckPart;
import de.jpx3.intave.detect.checks.other.InventoryClickAnalysis;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.service.violation.Violation;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class InventoryClickOnMoveCheck extends IntaveCheckPart<InventoryClickAnalysis> {
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
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();

    ClickType click = event.getClick();
    if (click == ClickType.CREATIVE) {
      return;
    }

    UserMetaMovementData movementData = meta.movementData();
    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;

    // Be more lenient when a flying packet was sent
    if (movementData.inWeb || movementData.recentlyEncounteredFlyingPacket(2)) {
      return;
    }

    if (keyForward != 0 || keyStrafe != 0) {
      String message = "performed inventory-click whilst walking";
      Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
        .withPlayer(player)
        .withMessage(message)
        .withVL(0)
        .build();
      plugin.violationProcessor().processViolation(violation);
      Synchronizer.synchronize(player::closeInventory);
      event.setCancelled(true);
    }
  }
}