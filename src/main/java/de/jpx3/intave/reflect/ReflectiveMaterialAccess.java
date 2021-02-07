package de.jpx3.intave.reflect;

import de.jpx3.intave.adapter.ProtocolLibAdapter;
import org.bukkit.Material;

public final class ReflectiveMaterialAccess {
  public static Material materialById(int id) {
    if (ProtocolLibAdapter.EXPLORATION_UPDATE.atOrAbove()) {
      return resolveIterative(id);
    } else {
      return Material.getMaterial(id);
    }
  }

  private static Material resolveIterative(int id) {
    for (Material value : Material.values()) {
      int valueId = value.getId();
      if (valueId == id) {
        return value;
      }
    }
    return null;
  }
}