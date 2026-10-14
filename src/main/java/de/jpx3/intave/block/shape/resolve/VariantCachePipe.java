package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.cleanup.ReferenceMap;
import de.jpx3.intave.diagnostic.MemoryWatchdog;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class VariantCachePipe implements ShapeResolverPipeline {
  private final ShapeResolverPipeline forward;
  private final Map<Material, /*(SoftReference)*/Map<Integer, BlockShape>> collisionShapeCache = MemoryWatchdog.watch("variant-collide-cache", new ConcurrentHashMap<>());
  private final Map<Material, /*(SoftReference)*/Map<Integer, BlockShape>> outlineShapeCache = MemoryWatchdog.watch("variant-outline-cache", new ConcurrentHashMap<>());

  public VariantCachePipe(ShapeResolverPipeline forward) {
    this.forward = forward;
    checkVersion();
  }

  private void checkVersion() {
    if (!MinecraftVersions.VER1_13_0.atOrAbove()) {
      throw new UnsupportedOperationException("Can't utilize variant cache on versions older than 1.13");
    }
  }

  @Override
  public BlockShape collisionShapeOf(World world, User user, Material type, int variantIndex, int posX, int posY, int posZ) {
    Map<Integer, BlockShape> variantCache = collisionShapeCache.computeIfAbsent(type, material -> ReferenceMap.soft(new ConcurrentHashMap<>()));
    return variantCache.computeIfAbsent(variantIndex, integer ->
      forward.collisionShapeOf(world, user, type, variantIndex, posX, posY, posZ).normalized(posX, posY, posZ)
    ).contextualized(posX, posY, posZ);
  }

  @Override
  public BlockShape outlineShapeOf(World world, User user, Material type, int variantIndex, int posX, int posY, int posZ) {
    Map<Integer, BlockShape> variantCache = outlineShapeCache.computeIfAbsent(type, material -> ReferenceMap.soft(new ConcurrentHashMap<>()));
    return variantCache.computeIfAbsent(variantIndex, integer ->
      forward.outlineShapeOf(world, user, type, variantIndex, posX, posY, posZ).normalized(posX, posY, posZ)
    ).contextualized(posX, posY, posZ);
  }

  @Override
  public void downstreamTypeReset(Material type) {
    collisionShapeCache.remove(type);
    outlineShapeCache.remove(type);
    forward.downstreamTypeReset(type);
  }
}
