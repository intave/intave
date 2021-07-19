package de.jpx3.intave.world.blockshape.resolver.pipeline;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.tools.MemoryWatchdog;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.blockshape.resolver.BoundingBoxResolvePipeline;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DynamicVariantCache implements BoundingBoxResolvePipeline {
  private final BoundingBoxResolvePipeline forward;
  private final Map<Material, Map<Integer, List<WrappedAxisAlignedBB>>> cache = MemoryWatchdog.watch("variant-cache", new ConcurrentHashMap<>());

  public DynamicVariantCache(BoundingBoxResolvePipeline forward) {
    this.forward = forward;
    checkVersion();
  }

  private void checkVersion() {
    if (!MinecraftVersions.VER1_14_0.atOrAbove()) {
      throw new UnsupportedOperationException("Can't utilize variant cache on versions older than 1.14");
    }
  }

  @Override
  @Deprecated
  public List<WrappedAxisAlignedBB> nativeResolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    Map<Integer, List<WrappedAxisAlignedBB>> variantCache = cache.computeIfAbsent(type, material -> new ConcurrentHashMap<>());
    return transpose(variantCache.computeIfAbsent(blockState, integer -> reposeIfRequired(forward.nativeResolve(world, player, type, blockState, posX, posY, posZ), posX, posY, posZ)), posX, posY, posZ);
  }

  @Override
  public List<WrappedAxisAlignedBB> customResolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    Map<Integer, List<WrappedAxisAlignedBB>> variantCache = cache.computeIfAbsent(type, material -> new ConcurrentHashMap<>());
    return transpose(variantCache.computeIfAbsent(blockState, integer -> reposeIfRequired(forward.customResolve(world, player, type, blockState, posX, posY, posZ), posX, posY, posZ)), posX, posY, posZ);
  }

  @Override
  public void flushTypeCache(Material type) {
    cache.remove(type);
    forward.flushTypeCache(type);
  }

  private static List<WrappedAxisAlignedBB> transpose(List<WrappedAxisAlignedBB> boundingBoxes, int posX, int posY, int posZ) {
    if (boundingBoxes.isEmpty()) {
      return Collections.emptyList();
    }
    List<WrappedAxisAlignedBB> result = new ArrayList<>(boundingBoxes.size());
    for (int i = 0; i < boundingBoxes.size(); i++) {
      WrappedAxisAlignedBB boundingBox = boundingBoxes.get(i);
      if (boundingBox.isOriginBox()) {
        result.add(i, boundingBox.offset(posX, posY, posZ));
      }
    }
    return result;
  }

  private static List<WrappedAxisAlignedBB> reposeIfRequired(List<WrappedAxisAlignedBB> boundingBoxes, int posX, int posY, int posZ) {
    if (boundingBoxes.isEmpty()) {
      return Collections.emptyList();
    }
    List<WrappedAxisAlignedBB> result = new ArrayList<>(boundingBoxes);
    for (int i = 0; i < result.size(); i++) {
      WrappedAxisAlignedBB boundingBox = result.get(i);
      WrappedAxisAlignedBB newBox = boundingBox.offset(-posX, -posY, -posZ);
      newBox.setOriginBox();
      result.set(i, newBox);
    }
    return result;
  }
}
