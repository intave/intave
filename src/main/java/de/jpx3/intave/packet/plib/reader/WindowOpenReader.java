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

package de.jpx3.intave.packet.plib.reader;

import de.jpx3.intave.adapter.MinecraftVersions;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class WindowOpenReader extends AbstractPacketReader implements EntityIterable {
  private static final String[] MENU_TYPES_1_14 = {
    "minecraft:generic_9x1",
    "minecraft:generic_9x2",
    "minecraft:generic_9x3",
    "minecraft:generic_9x4",
    "minecraft:generic_9x5",
    "minecraft:generic_9x6",
    "minecraft:generic_3x3",
    "minecraft:anvil",
    "minecraft:beacon",
    "minecraft:blast_furnace",
    "minecraft:brewing_stand",
    "minecraft:crafting",
    "minecraft:enchantment",
    "minecraft:furnace",
    "minecraft:grindstone",
    "minecraft:hopper",
    "minecraft:lectern",
    "minecraft:loom",
    "minecraft:merchant",
    "minecraft:shulker_box",
    "minecraft:smoker",
    "minecraft:cartography_table",
    "minecraft:stonecutter"
  };
  private static final String[] MENU_TYPES_1_16 = {
    "minecraft:generic_9x1",
    "minecraft:generic_9x2",
    "minecraft:generic_9x3",
    "minecraft:generic_9x4",
    "minecraft:generic_9x5",
    "minecraft:generic_9x6",
    "minecraft:generic_3x3",
    "minecraft:anvil",
    "minecraft:beacon",
    "minecraft:blast_furnace",
    "minecraft:brewing_stand",
    "minecraft:crafting",
    "minecraft:enchantment",
    "minecraft:furnace",
    "minecraft:grindstone",
    "minecraft:hopper",
    "minecraft:lectern",
    "minecraft:loom",
    "minecraft:merchant",
    "minecraft:shulker_box",
    "minecraft:smithing",
    "minecraft:smoker",
    "minecraft:cartography_table",
    "minecraft:stonecutter"
  };

  public int containerId() {
    return packet().getIntegers().read(0);
  }

  public String menuType() {
    if ("OPEN_WINDOW_HORSE".equals(packet().getType().name())) {
      return "minecraft:horse";
    }
    if (!MinecraftVersions.VER1_14_0.atOrAbove()) {
      String legacyType = packet().getStrings().readSafely(0);
      return legacyType == null || legacyType.isEmpty() ? "unknown" : legacyType;
    }

    Integer numericType = packet().getIntegers().readSafely(1);
    if (numericType != null) {
      String[] menuTypes = MinecraftVersions.VER1_16_0.atOrAbove()
        ? MENU_TYPES_1_16
        : MENU_TYPES_1_14;
      return numericType >= 0 && numericType < menuTypes.length
        ? menuTypes[numericType]
        : "unknown";
    }

    try {
      Class<?> menuType = packet().getModifier().getField(1).getType();
      Object registrable = packet().getRegistrableModifier(menuType).readSafely(0);
      if (registrable != null) {
        Object key = registrable.getClass().getMethod("getKey").invoke(registrable);
        if (key != null) {
          return String.valueOf(key.getClass().getMethod("getFullKey").invoke(key));
        }
      }
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      // ProtocolLib cannot expose registries on every supported server release.
    }
    return "unknown";
  }

  public int slots() {
    if (MinecraftVersions.VER1_19.atOrAbove()) {
//      Containers containers = packet().getSpecificModifier(Containers.class).read(0);
//      String lookupKey = RegistryKeyLookup.keyFrom("menu", containers);
//      String key = packet().getRegistrableModifier(Containers.class).read(0).getKey().getKey();
//      System.out.println(key + " / " + lookupKey);
//      switch (key) {
//        case "generic_9x1":
//        case "generic_3x3":
//          return 9;
//        case "generic_9x2":
//          return 18;
//        case "generic_9x3":
//        case "shulker_box":
//          return 27;
//        case "generic_9x4":
//          return 36;
//        case "generic_9x5":
//          return 45;
//        case "generic_9x6":
//          return 54;
//        case "anvil":
//        case "cartography_table":
//        case "smoker":
//        case "smithing":
//        case "merchant":
//        case "grindstone":
//        case "furnace":
//        case "blast_furnace":
//          return 3;
//        case "beacon":
//        case "lectern":
//          return 1;
//        case "brewing_stand":
//        case "hopper":
//          return 5;
//        case "crafting":
//          return 10;
//        case "enchantment":
//        case "stonecutter":
//          return 2;
//        case "loom":
//          return 4;
//      }
      return 27;
    } else if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      int menuType = packet().getIntegers().read(0);
      switch (menuType) {
        case 0:
          return 9;
        case 1:
          return 18;
        case 2:
          return 27;
        case 3:
          return 36;
        case 4:
          return 45;
        case 5:
          return 54;
        default:
          return 27;
      }
    } else {
      return packet().getIntegers().read(1);
    }
  }

  public Optional<Integer> optionalEntityId() {
    Integer read = packet().getIntegers().read(2);
    if (read == null) {
      return Optional.empty();
    } else if (read == 0) {
      return Optional.empty();
    }
    return Optional.of(read);
  }

  @Override
  public @NotNull SubstitutionIterator<Integer> iterator() {
    Optional<Integer> integer = optionalEntityId();
    if (!integer.isPresent()) {
      return new SubstitutionIterator<Integer>() {
        @Override
        public void set(Integer integer) {
        }

        @Override
        public boolean hasNext() {
          return false;
        }

        @Override
        public Integer next() {
          throw new UnsupportedOperationException();
        }
      };
    } else {
      return new SubstitutionIterator<Integer>() {
        boolean hasNext = false;

        @Override
        public void set(Integer integer) {
          packet().getIntegers().write(2, integer);
        }

        @Override
        public boolean hasNext() {
          return hasNext;
        }

        @Override
        public Integer next() {
          hasNext = false;
          return integer.get();
        }
      };
    }
  }
}
