package de.jpx3.intave.block.shape.pipe;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.cleanup.ReferenceMap;
import de.jpx3.intave.diagnostic.MemoryWatchdog;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VariantCachePipe implements ShapeResolverPipeline {
  private final ShapeResolverPipeline forward;
  private final Map<Material, /*SoftReference*/Map<Integer, BlockShape>> cache = MemoryWatchdog.watch("variant-cache", new ConcurrentHashMap<>());

  public VariantCachePipe(ShapeResolverPipeline forward) {
    this.forward = forward;
    checkVersion();
  }

  private void checkVersion() {
    if (!MinecraftVersions.VER1_14_0.atOrAbove()) {
      throw new UnsupportedOperationException("Can't utilize variant cache on versions older than 1.14");
    }
  }

  @Override
  public BlockShape resolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    Map<Integer, BlockShape> variantCache = cache.computeIfAbsent(type, material -> ReferenceMap.soft(new ConcurrentHashMap<>()));
    return variantCache.computeIfAbsent(blockState, integer ->
     forward.resolve(world, player, type, blockState, posX, posY, posZ).normalized(posX, posY, posZ)
    ).contextualized(posX, posY, posZ);
  }

  @Override
  public void downstreamTypeReset(Material type) {
    cache.remove(type);
    forward.downstreamTypeReset(type);
  }
}
