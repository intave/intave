package de.jpx3.intave.world.blockphysics;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

public final class BlockSlipperinessRepository {
  private static final Map<Material, Float> registry = new HashMap<>();

  public static void setup() {
    registry.put(Material.PACKED_ICE, 0.98f);
    registry.put(Material.ICE, 0.98f);
    registry.put(Material.SLIME_BLOCK, 0.8f);

    // special
    tryRegister("BLUE_ICE", 0.989F);
    tryRegister("FROSTED_ICE", 0.98F);
  }

  public static float resolveSlipperinessOf(Material material) {
    Float slipperiness = registry.get(material);
    return slipperiness == null ? 0.6f : slipperiness;
  }

  private static void tryRegister(String name, float slipperiness) {
    Material material = Material.getMaterial(name);
    if (material != null) {
      registry.put(material, slipperiness);
    }
  }
}