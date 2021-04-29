package de.jpx3.intave.detect.checks.other.inventoryclickanalysis;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.detect.CheckViolationLevelDecrementer;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.other.InventoryClickAnalysis;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.punishment.AttackNerfStrategy;
import de.jpx3.intave.event.service.violation.Violation;
import de.jpx3.intave.event.service.violation.ViolationContext;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.user.UserCustomCheckMeta;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;

import static de.jpx3.intave.detect.checks.other.InventoryClickAnalysis.MAX_VL_DECREMENT_PER_SECOND;

public final class InventoryClickDelayAnalyzer extends IntaveMetaCheckPart<InventoryClickAnalysis, InventoryClickDelayAnalyzer.ClickDelayMeta> {
  private final IntavePlugin plugin;
  private final boolean invalidVersion;

  public InventoryClickDelayAnalyzer(InventoryClickAnalysis parentCheck) {
    super(parentCheck, ClickDelayMeta.class);
    invalidVersion = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_11_0);
    plugin = IntavePlugin.singletonInstance();
  }

  @BukkitEventSubscription(priority = EventPriority.LOWEST)
  public void receiveInventoryClick(InventoryClickEvent event) {
    HumanEntity whoClicked = event.getWhoClicked();
    if (!(whoClicked instanceof Player)) {
      return;
    }
    Player player = (Player) whoClicked;

    if (player.getGameMode().equals(GameMode.CREATIVE) || invalidVersion || event.getCurrentItem() == null) {
      return;
    }

    ClickDelayMeta meta = metaOf(player);

    if (meta.lastClickedType != null && meta.lastClickedType.equals(event.getCurrentItem().getType())) {
      return;
    }

    int slot = event.getRawSlot();
    int lastSlot = meta.lastClickedSlot;

    double time = (AccessHelper.now() - meta.lastClickInv) / 1000d;
    double distance = distanceBetween(slot, lastSlot);

    double speedAttr = distance / time;

    boolean flag = speedAttr > 30;
    boolean flag2 = speedAttr > 100;

    if (distance > 2 && flag && (flag2 || AccessHelper.now() - meta.lastTimeEstimatedMousePositonMovedTooQuickly < 5000)) {
      Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
        .withPlayer(player).withDefaultThreshold()
        .withMessage("is switching too quickly between item slots")
        .withDetails("moved from slot " + lastSlot + " to slot " + slot + " in " + MathHelper.formatDouble(time, 3) + " seconds")
        .withVL(time > 0.01 ? 10 : 5).build();

      ViolationContext violationContext = plugin.violationProcessor().processViolation(violation);

      if (IntaveControl.GOMME_MODE) {
        if (distance > 0 && violationContext.violationLevelAfter() > 30) {
          userOf(player).applyAttackNerfer(AttackNerfStrategy.DMG_MEDIUM);
        }
      }
    }

    if (speedAttr < 10) {
      CheckViolationLevelDecrementer decrementer = parentCheck().decrementer();
      decrementer.decrement(userOf(player), MAX_VL_DECREMENT_PER_SECOND);
    }

    if (flag) {
      meta.lastTimeEstimatedMousePositonMovedTooQuickly = AccessHelper.now();
    }

    meta.lastClickedSlot = slot;
    meta.lastClickInv = AccessHelper.now();
    meta.lastClickedType = event.getCurrentItem().getType();
  }

  private double distanceBetween(int slot1, int slot2) {
    int[] slot1XZ = translatePosition(slot1);
    int[] slot2XZ = translatePosition(slot2);
    return Math.sqrt((slot1XZ[0] - slot2XZ[0]) * (slot1XZ[0] - slot2XZ[0]) + (slot1XZ[1] - slot2XZ[1]) * (slot1XZ[1] - slot2XZ[1]));
  }

  private int[] translatePosition(int slot) {
    int row = (slot / 9) + 1;
    int rowPosition = slot - ((row - 1) * 9);
    return new int[]{row, rowPosition};
  }

  public static final class ClickDelayMeta extends UserCustomCheckMeta {
    public Material lastClickedType;
    public int lastClickedSlot;
    public long lastClickInv;
    public long lastTimeEstimatedMousePositonMovedTooQuickly;
  }
}