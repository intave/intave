package de.jpx3.intave.detect.checks.other.inventoryclickanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.detect.MetaCheckPart;
import de.jpx3.intave.detect.checks.other.InventoryClickAnalysis;
import de.jpx3.intave.event.violation.Violation;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.reflect.Lookup;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.RotationUtilities;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.WINDOW_CLICK;

public final class InventoryClickDelayAnalyzer extends MetaCheckPart<InventoryClickAnalysis, InventoryClickDelayAnalyzer.ClickDelayMeta> {
  private final IntavePlugin plugin;
  private final boolean newWindowClickVersion;
  Class<?> clickType;

  public InventoryClickDelayAnalyzer(InventoryClickAnalysis parentCheck) {
    super(parentCheck, ClickDelayMeta.class);
    newWindowClickVersion = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0);
    plugin = IntavePlugin.singletonInstance();
    if (newWindowClickVersion) {
      clickType = Lookup.serverClass("InventoryClickType");
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      WINDOW_CLICK
    }
  )
  public void windowClickPacket(PacketEvent event) {
    Player player = event.getPlayer();
    if (player.getGameMode().equals(GameMode.CREATIVE)) {
      return;
    }
    if (ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_13_0)) {
      return;
    }
    User user = userOf(player);
    ClickDelayMeta meta = metaOf(user);

//    for (Object object : event.getPacket().getModifier().getValues()) {
//      String s;
//      if (object == null) {
//        s = "null";
//      } else {
//        s = "" + object + " " + object.getClass();
//      }
//      player.sendMessage(s);
//    }

    if (user.meta().protocol().protocolVersion() >= ProtocolMetadata.VER_1_12) {
      // TODO: when a player shifts an item in 1.12+ he sends a "null" as itemStack which makes the check imcompatible
      return;
    }

    int slot = event.getPacket().getIntegers().read(1);
    ItemStack itemStack = event.getPacket().getItemModifier().read(0);
    int clickedItemID = itemStack.getData().getItemTypeId() * 16 + itemStack.getData().getData();
    boolean droppedAnItem;
    if (newWindowClickVersion) {
      InventoryClickTypes clickTypes = event.getPacket().getEnumModifier(InventoryClickTypes.class, clickType).read(0);
      droppedAnItem = clickTypes == InventoryClickTypes.THROW && slot != -999;
    } else {
      droppedAnItem = event.getPacket().getIntegers().read(3) == 4 && slot != -999;
    }

    if (slot != -999 && meta.lastClickedSlot != -999) {
      if ((clickedItemID != meta.lastClickedItemID || droppedAnItem) && meta.lastClickedTimeStamp != 0) {
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
      if(isPartner()) {
        processStandardDeviationCheck(player, meta);
      }
      meta.clickDelayList.clear();
    }

    processClickDelayAnalyzerCheck(player, meta, slot, time);
  }

  @Native
  public boolean isPartner() {
    return (ProtocolMetadata.VERSION_DETAILS & 0x100) != 0;
  }

  private void processClickDelayAnalyzerCheck(Player player, ClickDelayMeta meta, int slot, double time) {
    double distance = distanceBetween(slot, meta.lastClickedSlot);
    double speedAttr = distance / time;

    boolean flag = speedAttr > 30;
    boolean flag2 = speedAttr > 100;

    if (distance > 2 && flag && (flag2 || AccessHelper.now() - meta.lastFlagTimeStamp < 5000)) {
      Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
        .forPlayer(player).withDefaultThreshold()
        .withMessage("is switching too quickly between item slots")
        .withDetails("moved from slot " + meta.lastClickedSlot + " to slot " + slot + " in " + MathHelper.formatDouble(time, 3) + " seconds")
        .withVL(time > 0.01 ? 10 : 5).build();

      //ViolationContext violationContext =
      plugin.violationProcessor().processViolation(violation);
//          if (IntaveControl.GOMME_MODE) {
//            if (distance > 0 && violationContext.violationLevelAfter() > 30) {
//              userOf(player).applyAttackNerfer(AttackNerfStrategy.DMG_MEDIUM);
//            }
//          }
    }

    if (flag) {
      meta.lastFlagTimeStamp = AccessHelper.now();
    }
  }

  private void processStandardDeviationCheck(Player player, ClickDelayMeta meta) {
    User user = userOf(player);
    double std = RotationUtilities.calculateStandardDeviation(meta.clickDelayList) * 100;

    double averageMovementPacketTimestamp = user.meta().connection().averageMovementPacketTimestamp();
    if (std < 2 && Math.abs(averageMovementPacketTimestamp - 50) < 40) {
      Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
        .forPlayer(player).withDefaultThreshold()
        .withMessage("is clicking suspiciously on items")
        .withDetails("time details: " + MathHelper.formatDouble(std, 2))
        .withVL(5).build();
//
      plugin.violationProcessor().processViolation(violation);
    }
  }

  private void prepareNextTick(User user, int slot, int itemID) {
    ClickDelayMeta meta = metaOf(user);
    meta.lastClickedSlot = slot;
    meta.lastClickedTimeStamp = System.nanoTime();
    meta.lastClickedItemID = itemID;
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
    private int lastClickedItemID;
    private long lastFlagTimeStamp;
  }

  @KeepEnumInternalNames
  public enum InventoryClickTypes {
    PICKUP,
    QUICK_MOVE,
    SWAP,
    CLONE,
    THROW,
    QUICK_CRAFT,
    PICKUP_ALL;
  }
}