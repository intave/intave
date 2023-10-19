package de.jpx3.intave.block.state;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.annotate.DoNotFlowObfuscate;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.diagnostic.ShapeAccessFlowStudy;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.world.WorldHeight;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.jpx3.intave.IntaveControl.DISABLE_BLOCK_CACHING_ENTIRELY;

@DoNotFlowObfuscate
final class MultiChunkKeyExtendedBlockStateCache implements ExtendedBlockStateCache {
  private final Player player;
  private final ShapeResolverPipeline shapeResolver;
  private final Map<Long, BlockState> blockCache = new ConcurrentHashMap<>(1024);
//  private final Map<Long, BlockState> blockCache = Long2ObjectMaps.synchronize(new Long2ObjectOpenCustomHashMap<>(new LongHash.Strategy() {
//    @Override
//    public int hashCode(long l) {
//      return Long.hashCode(l);
//    }
//
//    @Override
//    public boolean equals(long l, long l1) {
//      return l == l1;
//    }
//  }));
  private final BlockStateReplacementCache<Long> replacementCache;
  private int originChunkX, originChunkZ;
  private int chunkX, chunkZ;

  public MultiChunkKeyExtendedBlockStateCache(
    Player player, ShapeResolverPipeline resolver
  ) {
    this.player = player;
    this.shapeResolver = resolver;
    this.replacementCache = new BlockStateReplacementCache<>(player, MultiChunkKeyExtendedBlockStateCache::bigKey);
  }

  @Override
  public @NotNull BlockShape collisionShapeAt(int posX, int posY, int posZ) {
    if (posY < WorldHeight.LOWER_WORLD_LIMIT || WorldHeight.UPPER_WORLD_LIMIT < posY) {
      return BlockShapes.emptyShape();
    }
    int chunkX = posX >> 4, chunkZ = posZ >> 4;
    ShapeAccessFlowStudy.requests++;
    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      double distance = Hypot.fast(chunkX - originChunkX, chunkZ - originChunkZ);
      if (distance > 2 || blockCache.size() > 4096) {
        this.originChunkX = chunkX;
        this.originChunkZ = chunkZ;
        blockCache.clear();
      }
      purgeOverrides();
    }
    long key = bigKey(posX, posY, posZ);
    BlockState blockState = replacementCache.byKey(key);
    if (blockState != null) {
      return blockState.collisionShape();
    }
    blockState = blockCache.get(key);
    if (blockState == null) {
      World world = player.getWorld();
      Block block = VolatileBlockAccess.blockAccess(world, posX, posY, posZ);
      blockState = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= WorldHeight.LOWER_WORLD_LIMIT) {
        blockCache.put(key, blockState);
      }
    }
    return blockState.collisionShape();
  }

  @Override
  public @NotNull BlockShape outlineShapeAt(int posX, int posY, int posZ) {
    if (posY < WorldHeight.LOWER_WORLD_LIMIT || WorldHeight.UPPER_WORLD_LIMIT < posY) {
      return BlockShapes.emptyShape();
    }
    int chunkX = posX >> 4, chunkZ = posZ >> 4;
    ShapeAccessFlowStudy.requests++;
    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      double distance = Hypot.fast(chunkX - originChunkX, chunkZ - originChunkZ);
      if (distance > 2 || blockCache.size() > 4096) {
        this.originChunkX = chunkX;
        this.originChunkZ = chunkZ;
        blockCache.clear();
      }
      purgeOverrides();
    }
    long key = bigKey(posX, posY, posZ);
    BlockState blockState = replacementCache.byKey(key);
    if (blockState != null) {
      return blockState.outlineShape();
    }
    blockState = blockCache.get(key);
    if (blockState == null) {
      World world = player.getWorld();
      Block block = VolatileBlockAccess.blockAccess(world, posX, posY, posZ);
      blockState = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= WorldHeight.LOWER_WORLD_LIMIT) {
        blockCache.put(key, blockState);
      }
    }
    return blockState.outlineShape();
  }

  @Override
  public @NotNull Material typeAt(int posX, int posY, int posZ) {
    if (posY < WorldHeight.LOWER_WORLD_LIMIT || WorldHeight.UPPER_WORLD_LIMIT < posY) {
      return Material.AIR;
    }
    int chunkX = posX >> 4, chunkZ = posZ >> 4;
    ShapeAccessFlowStudy.requests++;
    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      double distance = Hypot.fast(chunkX - originChunkX, chunkZ - originChunkZ);
      if (distance > 2 || blockCache.size() > 4096) {
        this.originChunkX = chunkX;
        this.originChunkZ = chunkZ;
        blockCache.clear();
      }
      purgeOverrides();
    }
    long key = bigKey(posX, posY, posZ);
    BlockState blockState = replacementCache.byKey(key);
    if (blockState != null) {
      return blockState.type();
    }
    blockState = blockCache.get(key);
    if (blockState == null) {
      World world = player.getWorld();
      Block block = VolatileBlockAccess.blockAccess(world, posX, posY, posZ);
      blockState = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= WorldHeight.LOWER_WORLD_LIMIT) {
        blockCache.put(key, blockState);
      }
    }
    return blockState.type();
  }

  @Override
  public int variantIndexAt(int posX, int posY, int posZ) {
    if (posY < WorldHeight.LOWER_WORLD_LIMIT || WorldHeight.UPPER_WORLD_LIMIT < posY) {
      return 0;
    }
    int chunkX = posX >> 4, chunkZ = posZ >> 4;
    ShapeAccessFlowStudy.requests++;
    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      double distance = Hypot.fast(chunkX - originChunkX, chunkZ - originChunkZ);
      if (distance > 2 || blockCache.size() > 4096) {
        this.originChunkX = chunkX;
        this.originChunkZ = chunkZ;
        blockCache.clear();
      }
      purgeOverrides();
    }
    long key = bigKey(posX, posY, posZ);
    BlockState blockState = replacementCache.byKey(key);
    if (blockState != null) {
      return blockState.variantIndex();
    }
    blockState = blockCache.get(key);
    if (blockState == null) {
      World world = player.getWorld();
      Block block = VolatileBlockAccess.blockAccess(world, posX, posY, posZ);
      blockState = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= WorldHeight.LOWER_WORLD_LIMIT) {
        blockCache.put(key, blockState);
      }
    }
    return blockState.variantIndex();
  }

  private BlockState lookup(World world, Block block, int posX, int posY, int posZ) {
    if (block.getY() < WorldHeight.LOWER_WORLD_LIMIT) {
      return BlockState.empty();
    }
    Material type = BlockTypeAccess.typeAccess(block, player);
    if (type == Material.AIR) {
      return BlockState.empty();
    } else {
      ShapeAccessFlowStudy.incremLookups();
      int variant = BlockVariantNativeAccess.variantAccess(block);
      BlockShape outlineShape = shapeResolver.outlineShapeOf(world, player, type, variant, posX, posY, posZ);
      BlockShape collisionShape = shapeResolver.collisionShapeOf(world, player, type, variant, posX, posY, posZ);
      return new BlockState(outlineShape, collisionShape, type, variant);
    }
  }

  @Override
  public void invalidateAll() {
    invalidateCache();
    replacementCache.clear();
//    player.sendMessage(ChatColor.GOLD + "invalidateAll()");
  }

  @Override
  public void invalidateCache() {
    blockCache.clear();
  }

  @Override
  public void invalidateCacheAt0(int posX, int posY, int posZ) {
    blockCache.remove(bigKey(posX, posY, posZ));
//    player.sendMessage(ChatColor.GOLD + "invalidateCacheAt0("+posX+", "+posY+", "+posZ+")");
  }

  @Override
  public void override(World world, int posX, int posY, int posZ, Material type, int variant, String reason) {
//    invalidateOverride(posX, posY, posZ);
    long key = bigKey(posX, posY, posZ);
    replacementCache.remove(key);
    BlockState blockState;
    if (type == Material.AIR) {
      blockState = BlockState.empty();
    } else {
      BlockShape shape = shapeResolver.collisionShapeOf(world, player, type, variant, posX, posY, posZ);
      blockState = new BlockState(shape, shape, type, variant);
    }
    Position position = new Position(posX, posY, posZ);
    replacementCache.insert(position, blockState);
    if (IntaveControl.BLOCK_CACHE_DEBUG) {
      Synchronizer.synchronize(() -> {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "OVERRIDE " + ChatColor.AQUA  + type + ChatColor.LIGHT_PURPLE + " at " + ChatColor.GRAY + position + ChatColor.LIGHT_PURPLE + " for " + ChatColor.RED + reason);
      });
    }
  }

  @Override
  public void invalidateOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
    replacementCache.chunkReset(chunkXMinPos, chunkXMaxPos, chunkZMinPos, chunkZMaxPos);
  }

  @Override
  public boolean currentlyInOverride(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    return replacementCache.replaced(key);
  }

  @Override
  public BlockState overrideOf(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    return replacementCache.byKey(key);
  }

  @Override
  public void lockOverride(int posX, int posY, int posZ) {
    replacementCache.lock(Position.of(posX, posY, posZ));
    if (IntaveControl.BLOCK_CACHE_DEBUG) {
      Synchronizer.synchronize(() -> {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "LOCK " + ChatColor.GRAY + Position.of(posX, posY, posZ));
      });
    }
  }

  @Override
  public void unlockOverride(int posX, int posY, int posZ) {
    if (replacementCache.unlock(Position.of(posX, posY, posZ))) {
      if (IntaveControl.BLOCK_CACHE_DEBUG) {
        Synchronizer.synchronize(() -> {
          player.sendMessage(ChatColor.LIGHT_PURPLE + "UNLOCK " + ChatColor.GRAY + Position.of(posX, posY, posZ));
        });
      }
    }
  }

  @Override
  public void invalidateOverride(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    replacementCache.remove(key);
//    player.sendMessage(ChatColor.GOLD + "invalidateOverride("+posX+", "+posY+", "+posZ+")");
  }

  private long lastPurge = System.currentTimeMillis();

  public void purgeOverrides() {
    if (System.currentTimeMillis() - lastPurge > 500) {
      lastPurge = System.currentTimeMillis();
      replacementCache.internalRefresh();
    }
  }

  @Override
  public int numOfIndexedReplacements() {
    return replacementCache.indexed().size();
  }

  @Override
  public int numOfLocatedReplacements() {
    return replacementCache.located().size();
  }

  private static long bigKey(Position location) {
    return bigKey(location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  private static long bigKey(int posX, int posY, int posZ) {
    return (posX & 0x3fffffL) << 42 | (posY & 0xfffffL) | (posZ & 0x3fffffL) << 20;
  }

  private static int smallKey(Position position) {
    return smallKey(position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  private static int smallKey(int posX, int posY, int posZ) {
    return (posX & 0x3fff) << 20 | (posY & 0x3ff) << 8 | (posZ & 0x3fff);
  }
}
