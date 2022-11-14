package de.jpx3.intave.block.type;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

public final class MaterialSearch {
  public static Material materialThatIsNamed(String name) {
    return findBy(material -> material.name().equals(name));
  }

  public static Material findBy(Predicate<? super Material> predicate) {
    for (Material material : Material.values()) {
      if (predicate.test(material)) {
        return material;
      }
    }
    return null;
  }

  public static Set<Material> materialsThatContain(String... searches) {
    return filterBy(material -> Arrays.stream(searches).anyMatch(search -> material.name().toLowerCase().contains(search.toLowerCase())));
  }

  public static Set<Material> filterBy(Predicate<? super Material> predicate) {
    Set<Material> materials = EnumSet.noneOf(Material.class);
    for (Material material : Material.values()) {
      if (predicate.test(material)) {
        materials.add(material);
      }
    }
    return materials;
  }
}
