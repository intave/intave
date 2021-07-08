package de.jpx3.intave.world.blockphysic;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public final class Climbables {
  private static final List<Material> registry = new ArrayList<>();

  public static void setup() {
    registry.add(Material.VINE);
    registry.add(Material.LADDER);

    // special
    tryRegister("SCAFFOLDING");
    tryRegister("WEEPING_VINES");
    tryRegister("WEEPING_VINES_PLANT");
    tryRegister("TWISTING_VINES");
    tryRegister("TWISTING_VINES_PLANT");
  }

  public static boolean canBeClimbed(Material material) {
    return registry.contains(material);
  }

  private static void tryRegister(String name) {
    Material material = Material.getMaterial(name);
    if (material != null) {
      registry.add(material);
    }
  }
}