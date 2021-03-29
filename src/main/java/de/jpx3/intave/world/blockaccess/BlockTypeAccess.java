package de.jpx3.intave.world.blockaccess;

import de.jpx3.intave.access.IntaveInternalException;
import org.bukkit.Material;

public final class BlockTypeAccess {
  public static final Material WEB = resolveFrom("WEB", "COBWEB");
  public static final Material SNOW_LAYER = resolveFrom("SNOW", "SNOW_LAYER");
  public static final Material TRAP_DOOR = resolveFrom("TRAP_DOOR", "LEGACY_TRAP_DOOR");

  private static Material resolveFrom(String name, String alternativeName) {
    Material material = Material.getMaterial(name);
    if (material != null) {
      return material;
    }
    Material alternativeMaterial = Material.getMaterial(alternativeName);
    if (alternativeMaterial != null) {
      return alternativeMaterial;
    } else {
      throw new IntaveInternalException("Unable to find block " + name + " or " + alternativeName);
    }
  }
}