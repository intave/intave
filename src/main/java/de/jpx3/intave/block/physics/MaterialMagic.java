package de.jpx3.intave.block.physics;

import de.jpx3.intave.annotate.refactoring.IdoNotBelongHere;
import de.jpx3.intave.block.type.BlockTypeAccess;
import org.bukkit.Material;

@IdoNotBelongHere
public final class MaterialMagic {
  public static boolean blocksMovement(Material material) {
    // Liquids don't block movement
    if (isLiquid(material)) {
      return false;
    }
    if (material == BlockTypeAccess.WEB) {
      return false;
    }
    // Materials of MaterialLogic and MaterialTransparent override blocksMovement() with "false"
    return !includesMaterialLogic(material) && !includesMaterialTransparent(material);
  }

  public static boolean blockSolid(Material material) {
    // Liquids aren't solid
    if (isLiquid(material) || material.isTransparent()) {
      return false;
    }
    // Materials of MaterialLogic and MaterialTransparent override isSolid() with "false"
    return !includesMaterialLogic(material) && !includesMaterialTransparent(material);
  }

  private static boolean includesMaterialLogic(Material material) {
    switch (material) {
      // Material Logic
      // - portal
      case ENDER_PORTAL:
        // - Snow
      case SNOW:
        // - Carpet
      case CARPET:
        // - Circuits
      case STONE_BUTTON:
      case WOOD_BUTTON:
      case FLOWER_POT_ITEM:
      case LADDER:
      case LEVER:
      case REDSTONE_WIRE:
      case DIODE:
      case DIODE_BLOCK_OFF:
      case DIODE_BLOCK_ON:
      case SKULL:
      case REDSTONE_TORCH_ON:
      case REDSTONE_COMPARATOR_OFF:
      case TRIPWIRE:
      case TRIPWIRE_HOOK:
        // - Vines
      case DEAD_BUSH:
      case CHORUS_PLANT:
      case DOUBLE_PLANT:
      case LONG_GRASS:
      case VINE:
        // - Plants
        // BLOCK_BUSH?
        // BLOCK_REED?
      case NETHER_WART_BLOCK:
      case COCOA: {
        return true;
      }
      default: {
        return false;
      }
    }
  }

  private static boolean includesMaterialTransparent(Material material) {
    switch (material) {
      case AIR:
      case FIRE: {
        return true;
      }
      default: {
        return false;
      }
    }
  }

  private final static Material STATIONARY_WATER = Material.getMaterial("STATIONARY_WATER");
  private final static Material STATIONARY_LAVA = Material.getMaterial("STATIONARY_LAVA");

  public static boolean isLiquid(Material material) {
    return isLava(material) || isWater(material);
  }

  public static boolean isLava(Material material) {
    return (STATIONARY_LAVA != null && material == STATIONARY_LAVA) || material == Material.LAVA;
  }

  public static boolean isWater(Material material) {
    return (STATIONARY_WATER != null && material == STATIONARY_WATER) || material == Material.WATER;
  }
}