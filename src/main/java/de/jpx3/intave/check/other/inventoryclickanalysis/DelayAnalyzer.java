package de.jpx3.intave.check.other.inventoryclickanalysis;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.InventoryClickAnalysis;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.WINDOW_CLICK;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.*;

public final class DelayAnalyzer extends MetaCheckPart<InventoryClickAnalysis, DelayAnalyzer.ClickDelayMeta> {
  private static final boolean MODERN_WINDOW_CLICK = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0);

  private final IntavePlugin plugin;
  private final boolean highToleranceMode;
  private final Class<?> clickType;

  public DelayAnalyzer(InventoryClickAnalysis parentCheck, boolean highToleranceMode) {
    super(parentCheck, ClickDelayMeta.class);
    this.highToleranceMode = highToleranceMode;
    this.plugin = IntavePlugin.singletonInstance();
    this.clickType = MODERN_WINDOW_CLICK ? Lookup.serverClass("InventoryClickType") : null;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      WINDOW_CLICK
    }
  )
  public void windowClickPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    if (player.getGameMode().equals(GameMode.CREATIVE)) {
      return;
    }
    if (ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_13_0)) {
      return;
    }
    User user = userOf(player);
    ClickDelayMeta meta = metaOf(user);

    if (user.protocolVersion() >= ProtocolMetadata.VER_1_12) {
      // TODO: when a player shifts an item in 1.12+ he sends a "null" as itemStack which makes the check imcompatible
      return;
    }

    int slot = event.getPacket().getIntegers().read(1);
    ItemStack itemStack = event.getPacket().getItemModifier().read(0);
    Material clickedItemID = itemStack == null ? Material.AIR : itemStack.getType();
    boolean droppedAnItem;
    if (MODERN_WINDOW_CLICK) {
      InventoryClickTypes clickTypes = event.getPacket().getEnumModifier(InventoryClickTypes.class, clickType).read(0);
      droppedAnItem = clickTypes == InventoryClickTypes.THROW && slot != -999;
    } else {
      droppedAnItem = event.getPacket().getIntegers().read(3) == 4 && slot != -999;
    }

    if (slot != -999 && meta.lastClickedSlot != -999) {
      if ((clickedItemID != meta.lastClickedMaterial || droppedAnItem) && meta.lastClickedTimeStamp != 0) {
        checkWindowClick(player, meta, slot);
      }
    }

    prepareNextTick(user, slot, clickedItemID);
  }

  private void checkWindowClick(Player player, ClickDelayMeta meta, int slot) {
    double time = (System.nanoTime() - meta.lastClickedTimeStamp) / 1000000000d;
    if (time < 2) {
      meta.clickDelayList.add(time);
    }
    if (meta.clickDelayList.size() > 10) {
      if (isPartner()) {
        processStandardDeviationCheck(player, meta);
      }
      meta.clickDelayList.clear();
    }
    processClickDelayAnalyzerCheck(player, meta, slot, time);
  }

  private boolean isPartner() {
    return (ProtocolMetadata.VERSION_DETAILS & 0x100) != 0;
  }

  private void processClickDelayAnalyzerCheck(Player player, ClickDelayMeta meta, int slot, double time) {
    double distance = distanceBetween(slot, meta.lastClickedSlot);
    double speedAttr = distance / time;

    boolean flag = speedAttr > (highToleranceMode ? 60 : 30);
    boolean flag2 = speedAttr > (highToleranceMode ? 150 : 100);

    if (distance > 2 && flag && (flag2 || System.currentTimeMillis() - meta.lastFlagTimeStamp < 5000)) {
      Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
        .forPlayer(player).withDefaultThreshold()
        .withMessage("is switching too quickly between item slots")
        .withDetails("moved from slot " + meta.lastClickedSlot + " to slot " + slot + " in " + MathHelper.formatDouble(time, 3) + " seconds")
        .withVL(5).build();
    }

    if (flag) {
      meta.lastFlagTimeStamp = System.currentTimeMillis();
    }
  }

  private void processStandardDeviationCheck(Player player, ClickDelayMeta meta) {
    User user = userOf(player);
    double std = standardDeviation(meta.clickDelayList) * 100;

    double averageMovementPacketTimestamp = user.meta().connection().averageMovementPacketTimestamp();
    if (std < 2 && Math.abs(averageMovementPacketTimestamp - 50) < 40) {
      Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
        .forPlayer(player).withDefaultThreshold()
        .withMessage("is clicking with regular deviation on items")
        .withDetails(MathHelper.formatDouble(std, 2) + " deviation")
        .withVL(5).build();
//
      Modules.violationProcessor().processViolation(violation);
    }
  }

  private double standardDeviation(List<? extends Number> sd) {
    double sum = 0, newSum = 0;
    for (Number v : sd) {
      sum = sum + v.doubleValue();
    }
    double mean = sum / sd.size();
    for (Number v : sd) {
      newSum = newSum + (v.doubleValue() - mean) * (v.doubleValue() - mean);
    }
    return Math.sqrt(newSum / sd.size());
  }

  private void prepareNextTick(User user, int slot, Material itemID) {
    ClickDelayMeta meta = metaOf(user);
    meta.lastClickedSlot = slot;
    meta.lastClickedTimeStamp = System.nanoTime();
    meta.lastClickedMaterial = itemID;
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

  public static final class ClickDelayMeta extends CheckCustomMetadata {
    List<Double> clickDelayList = new ArrayList<>();
    private int lastClickedSlot;
    private long lastClickedTimeStamp;
    private Material lastClickedMaterial;
    private long lastFlagTimeStamp;
  }

  public enum InventoryClickTypes {
    PICKUP,
    QUICK_MOVE,
    SWAP,
    CLONE,
    THROW,
    QUICK_CRAFT,
    PICKUP_ALL
  }
}