package de.jpx3.intave.diagnostic;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

public final class BlockPatchStudy {
  private final static Map<Material, Long> patches = new HashMap<>(256);

  public static synchronized void patched(Material material) {
    Long count = patches.get(material);
    if (count == null) {
      count = 0L;
    }
    count++;
    patches.put(material, count);
  }

  public static Map<Material, Long> patchCounts() {
    return patches;
  }
}
