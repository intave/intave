package de.jpx3.intave.check.other.inventoryclickanalysis;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.InventoryClickAnalysis;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.InventoryMetadata;
import org.bukkit.entity.Player;

import java.util.Locale;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.WINDOW_CLICK;

public final class InventoryClickNotOpenCheck extends CheckPart<InventoryClickAnalysis> {
  private final IntavePlugin plugin;

  public InventoryClickNotOpenCheck(InventoryClickAnalysis parentCheck) {
    super(parentCheck);
    plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      WINDOW_CLICK
    }
  )
  public void receiveWindowClick(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketContainer packet = event.getPacket();
    InventoryMetadata inventory = user.meta().inventory();

    StructureModifier<Integer> integers = packet.getIntegers();
    int container = integers.read(0);
    int slot = integers.read(1);
    InventoryClickType clickType = readClickTypeFrom(packet);

    boolean isNativeInventoryClick = container == 0;
    boolean forceInventoryOnClickOpen = user.meta().inventory().forceInventoryOnClickOpen;

    // Do not remove! @Richy
    if (!forceInventoryOnClickOpen) {
      return;
    }

    if (!inventory.inventoryOpen()) {
      if (user.meta().protocol().supportsInventoryAchievementPacket()) {
        Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
          .forPlayer(player)
          .withMessage("clicked in closed inventory")
          .withDetails(clickType.name().toLowerCase(Locale.ROOT) + " on " + slot + "s/" + container + "c")
          .withVL(5).build();
        Modules.violationProcessor().processViolation(violation);
        Synchronizer.synchronize(player::updateInventory);
        event.setCancelled(true);
      } else if (isNativeInventoryClick) {
        user.meta().inventory().updateInventoryOpenState(true);
      }
    }
  }

  private final static Class<?> NATIVE_INVENTORY_CLICK_TYPE_CLASS = MinecraftVersions.VER1_9_0.atOrAbove() ? Lookup.serverClass("InventoryClickType") : Object.class;

  private InventoryClickType readClickTypeFrom(PacketContainer packet) {
    if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      return packet.getEnumModifier(InventoryClickType.class, NATIVE_INVENTORY_CLICK_TYPE_CLASS).read(0);
    } else {
      Integer manualSlot = packet.getIntegers().readSafely(3);
      return InventoryClickType.values()[manualSlot];
    }
  }

  @KeepEnumInternalNames
  public enum InventoryClickType {
    PICKUP,
    QUICK_MOVE,
    SWAP,
    CLONE,
    THROW,
    QUICK_CRAFT,
    PICKUP_ALL;
  }
}