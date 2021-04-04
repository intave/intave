package de.jpx3.intave.reflect;

import com.google.common.collect.Maps;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import org.bukkit.Material;

import java.util.Map;

public final class ReflectiveMaterialAccess {
  @Deprecated
  public static Material materialById(int id) {
    if (ProtocolLibAdapter.EXPLORATION_UPDATE.atOrAbove()) {
      return resolveIterative(id);
    } else {
      return Material.getMaterial(id);
    }
  }

  private static final Map<Integer, Material> resolverCache = Maps.newHashMap();

  private static Material resolveIterative(int id) {
    Material material = resolverCache.get(id);
    if (material != null) {
      return material;
    }
    for (Material selectedMaterial : Material.values()) {
      if (selectedMaterial.getId() == id) {
        resolverCache.put(id, selectedMaterial);
        return selectedMaterial;
      }
    }
    return null;
  }
}