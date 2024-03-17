package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class WindowClickReader extends AbstractPacketReader {
  private static final Class<?> NATIVE_INVENTORY_CLICK_TYPE_CLASS = MinecraftVersions.VER1_9_0.atOrAbove() ? Lookup.serverClass("InventoryClickType") : Object.class;
  private static final boolean MODERN_WINDOW_CLICK = MinecraftVersions.VER1_9_0.atOrAbove();

  public InventoryClickType clickType() {
    if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      return packet().getEnumModifier(InventoryClickType.class, NATIVE_INVENTORY_CLICK_TYPE_CLASS).read(0);
    } else {
      Integer manualSlot = packet().getIntegers().readSafely(3);
      return InventoryClickType.values()[manualSlot];
    }
  }

  public int container() {
    return packet().getIntegers().readSafely(0);
  }

  public String clickedItemTypeIfPossible(Player player) {
    if (container() == 0 && slot() >= 0) {
      User user = UserRepository.userOf(player);
      List<String> items = user.meta().inventory().items();
      int slot = slot();
      return items == null || slot >= items.size() ? null : items.get(slot);
    } else {
      return null;
    }
  }

  private static final int SLOT_ID = MinecraftVersions.VER1_17_1.atOrAbove() ? 2 : 1;

  public int slot() {
    return packet().getIntegers().readSafely(SLOT_ID);
  }

  private static final int BUTTON_ID = MinecraftVersions.VER1_17_1.atOrAbove() ? 3 : 2;

  public int button() {
    return packet().getIntegers().readSafely(BUTTON_ID);
  }

  public int actionNumber() {
    StructureModifier<Integer> integers = packet().getIntegers();
    if (integers.size() == 4) {
      return integers.readSafely(3);
    } else {
      return packet().getShorts().readSafely(0);
    }
  }

  public ItemStack itemStack() {
    return packet().getItemModifier().read(0);
  }

  public boolean isDrop() {
    if (MODERN_WINDOW_CLICK) {
      return clickType() == InventoryClickType.THROW && slot() != -999;
    } else {
      return packet().getIntegers().read(3) == 4 && slot() != -999;
    }
  }

  public boolean missingItemStack() {
    switch (clickType()) {
      case QUICK_MOVE:
      case SWAP:
//      case PICKUP_ALL:
        return true;
      default:
        return false;
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
    PICKUP_ALL
  }
}
