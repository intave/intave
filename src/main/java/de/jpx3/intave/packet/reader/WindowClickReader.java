/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.packet.reader;

import ac.intave.samples.event.InventoryActionEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.BukkitConverters;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.share.WindowClickType;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WindowClickReader extends AbstractPacketReader {
  private static final Map<Class<?>, HashedStackAccess> HASHED_STACK_ACCESS =
    new ConcurrentHashMap<>();
  private static Class<?> nativeWindowClickTypeClass;

  public WindowClickType clickType() {
    if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      return packet().getEnumModifier(
        WindowClickType.class, nativeWindowClickTypeClass()
      ).read(0);
    } else {
      Integer manualSlot = packet().getIntegers().readSafely(3);
      return WindowClickType.values()[manualSlot];
    }
  }

  public int containerId() {
    return packet().getIntegers().readSafely(0);
  }

  public String clickedItemTypeIfPossible(Player player) {
    if (containerId() == 0 && slot() >= 0) {
      User user = UserRepository.userOf(player);
      List<String> items = user.meta().inventory().items();
      int slot = slot();
      return items == null || slot >= items.size() ? null : items.get(slot);
    } else {
      return null;
    }
  }

  public int slot() {
    int slotId = MinecraftVersions.VER1_17_1.atOrAbove() ? 2 : 1;
    Integer integer = packet().getIntegers().readSafely(slotId);
    if (integer == null) {
      return packet().getShorts().readSafely(0);
    }
    return integer;
  }

  public int button() {
    int buttonId = MinecraftVersions.VER1_17_1.atOrAbove() ? 3 : 2;
    Integer integer = packet().getIntegers().readSafely(buttonId);
    if (integer == null) {
      return packet().getBytes().readSafely(0);
    }
    return integer;
  }

  private static Class<?> nativeWindowClickTypeClass() {
    if (nativeWindowClickTypeClass == null) {
      nativeWindowClickTypeClass = Lookup.serverClass("WindowClickType");
    }
    return nativeWindowClickTypeClass;
  }

  public InventoryActionEvent.Action action() {
    return actionOf(clickType(), slot(), button());
  }

  static InventoryActionEvent.Action actionOf(
    WindowClickType clickType, int slot, int button
  ) {
    switch (clickType) {
      case PICKUP:
        if (slot == -999) {
          return button == 0
            ? InventoryActionEvent.Action.DROP_CURSOR_STACK
            : button == 1
              ? InventoryActionEvent.Action.DROP_CURSOR_ONE
              : InventoryActionEvent.Action.UNKNOWN;
        }
        return button == 0
          ? InventoryActionEvent.Action.CLICK_PRIMARY
          : button == 1
            ? InventoryActionEvent.Action.CLICK_SECONDARY
            : InventoryActionEvent.Action.UNKNOWN;
      case QUICK_MOVE:
        return InventoryActionEvent.Action.QUICK_MOVE;
      case SWAP:
        return button >= 0 && button <= 8
          ? InventoryActionEvent.Action.SWAP_HOTBAR
          : button == 40
            ? InventoryActionEvent.Action.SWAP_OFFHAND
            : InventoryActionEvent.Action.UNKNOWN;
      case CLONE:
        return InventoryActionEvent.Action.CLONE;
      case THROW:
        return button == 0
          ? InventoryActionEvent.Action.DROP_SLOT_ONE
          : button == 1
            ? InventoryActionEvent.Action.DROP_SLOT_STACK
            : InventoryActionEvent.Action.UNKNOWN;
      case QUICK_CRAFT:
        switch (button & 3) {
          case 0:
            return InventoryActionEvent.Action.DRAG_START;
          case 1:
            return InventoryActionEvent.Action.DRAG_ADD_SLOT;
          case 2:
            return InventoryActionEvent.Action.DRAG_END;
          default:
            return InventoryActionEvent.Action.UNKNOWN;
        }
      case PICKUP_ALL:
        return InventoryActionEvent.Action.COLLECT_TO_CURSOR;
      default:
        return InventoryActionEvent.Action.UNKNOWN;
    }
  }

  public int actionNumber() {
    StructureModifier<Integer> integers = packet().getIntegers();
    if (integers.size() == 4) {
      return integers.readSafely(3);
    } else {
      return packet().getShorts().readSafely(0);
    }
  }

  /** Legacy action number or modern container state ID. */
  public Integer revision() {
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      return packet().getIntegers().readSafely(1);
    }
    return actionNumber();
  }

  public ItemStack itemStack() {
    return packet().getItemModifier().readSafely(0);
  }

  public boolean carriedItemKnown() {
    return MinecraftVersions.VER1_17_0.atOrAbove();
  }

  /** The client's predicted cursor stack on modern container-click packets. */
  public ItemStack carriedItem() {
    if (!carriedItemKnown()) {
      return null;
    }
    ItemStack item = packet().getItemModifier().readSafely(0);
    if (item != null) {
      return item;
    }
    List<Object> values = packet().getModifier().getValues();
    for (int index = 0; index + 1 < values.size(); index++) {
      if (values.get(index) instanceof Map) {
        return itemStack(values.get(index + 1));
      }
    }
    return null;
  }

  /** The client's predicted changed slots on modern container-click packets. */
  public Map<Integer, ItemStack> predictedSlots() {
    if (!MinecraftVersions.VER1_17_0.atOrAbove()) {
      return Collections.emptyMap();
    }
    Object rawMap = null;
    for (Object value : packet().getModifier().getValues()) {
      if (value instanceof Map) {
        rawMap = value;
        break;
      }
    }
    if (!(rawMap instanceof Map)) {
      return Collections.emptyMap();
    }
    Map<Integer, ItemStack> converted = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawMap).entrySet()) {
      if (!(entry.getKey() instanceof Number)) {
        continue;
      }
      converted.put(
        ((Number) entry.getKey()).intValue(), itemStack(entry.getValue())
      );
    }
    return converted;
  }

  private static ItemStack itemStack(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof ItemStack) {
      return (ItemStack) value;
    }
    if (MinecraftReflection.isItemStack(value)) {
      return BukkitConverters.getItemStackConverter().getSpecific(value);
    }

    // Since 1.21.5, container clicks contain HashedStack values rather than
    // full ItemStacks. ActualItem retains the item holder and count, but only
    // hashes component values. Rebuild the portion of the stack that exists on
    // the wire so consumers still receive its material and amount.
    Object nativeItem = recreateNativeItemStack(
      value, MinecraftReflection.getItemStackClass()
    );
    return nativeItem == null
      ? null
      : BukkitConverters.getItemStackConverter().getSpecific(nativeItem);
  }

  static Object recreateNativeItemStack(Object hashedStack, Class<?> nativeItemStackClass) {
    HashedStackAccess access = HASHED_STACK_ACCESS.computeIfAbsent(
      hashedStack.getClass(), type -> HashedStackAccess.create(type, nativeItemStackClass)
    );
    return access.recreate(hashedStack);
  }

  private static final class HashedStackAccess {
    private static final HashedStackAccess EMPTY = new HashedStackAccess(null, null, null);

    private final Field item;
    private final Field count;
    private final Constructor<?> itemStackConstructor;

    private HashedStackAccess(
      Field item, Field count, Constructor<?> itemStackConstructor
    ) {
      this.item = item;
      this.count = count;
      this.itemStackConstructor = itemStackConstructor;
    }

    private static HashedStackAccess create(
      Class<?> hashedStackClass, Class<?> nativeItemStackClass
    ) {
      try {
        Field count = null;
        for (Field field : hashedStackClass.getDeclaredFields()) {
          if (!Modifier.isStatic(field.getModifiers()) && field.getType() == Integer.TYPE) {
            count = field;
            break;
          }
        }
        if (count == null) {
          return EMPTY;
        }

        for (Constructor<?> constructor : nativeItemStackClass.getDeclaredConstructors()) {
          Class<?>[] parameters = constructor.getParameterTypes();
          if (parameters.length != 2 || parameters[1] != Integer.TYPE) {
            continue;
          }
          for (Field field : hashedStackClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
              || !parameters[0].isAssignableFrom(field.getType())) {
              continue;
            }
            field.setAccessible(true);
            count.setAccessible(true);
            constructor.setAccessible(true);
            return new HashedStackAccess(field, count, constructor);
          }
        }
      } catch (RuntimeException ignored) {
        // Treat inaccessible or unfamiliar hashed-stack implementations as empty.
      }
      return EMPTY;
    }

    private Object recreate(Object hashedStack) {
      if (itemStackConstructor == null) {
        return null;
      }
      try {
        return itemStackConstructor.newInstance(item.get(hashedStack), count.getInt(hashedStack));
      } catch (ReflectiveOperationException | RuntimeException ignored) {
        return null;
      }
    }
  }

  public boolean isDrop() {
    if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      return clickType() == WindowClickType.THROW && slot() != -999;
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
}
