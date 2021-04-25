package de.jpx3.intave.detect.checks.other;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.service.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaInventoryData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class InventoryClickAnalysis extends IntaveCheck {
  private final IntavePlugin plugin;

  public InventoryClickAnalysis(IntavePlugin plugin) {
    super("InventoryClickAnalysis", "inventoryclickanalysis");
    this.plugin = plugin;
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
    UserMetaInventoryData inventoryData = meta.inventoryData();
    boolean inventoryOpen = inventoryData.inventoryOpen();

    ClickType click = event.getClick();
    if (click == ClickType.CREATIVE) {
      return;
    }

    if (inventoryData.forceInventoryOnClickOpen && !inventoryOpen) {
      String message = "insufficient inventory-click (inventory not open)";
      Violation violation = Violation.fromType(InventoryClickAnalysis.class)
        .withPlayer(player).withMessage(message)
        .withVL(1)
        .build();
      plugin.violationProcessor().processViolation(violation);
      event.setCancelled(true);
    }
  }
}