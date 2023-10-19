package de.jpx3.intave.block.state;

import de.jpx3.intave.block.access.BlockAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class PassthroughBlockStateCache implements ExtendedBlockStateCache {
  private final Player player;
  private final ShapeResolverPipeline resolver;

  public PassthroughBlockStateCache(Player player, ShapeResolverPipeline resolver) {
    this.player = player;
    this.resolver = resolver;
  }

  @Override
  public @NotNull BlockShape outlineShapeAt(int posX, int posY, int posZ) {
    Block block = VolatileBlockAccess.blockAccess(player.getWorld(), posX, posY, posZ);
    Material type = block.getType();
    int variant = BlockAccess.global().variantIndexOf(block);

    return resolver.outlineShapeOf(
      player.getWorld(), player, type, variant, posX, posY, posZ
    );
  }

  @Override
  public @NotNull BlockShape collisionShapeAt(int posX, int posY, int posZ) {
    Block block = VolatileBlockAccess.blockAccess(player.getWorld(), posX, posY, posZ);
    Material type = block.getType();
    int variant = BlockAccess.global().variantIndexOf(block);

    return resolver.collisionShapeOf(
      player.getWorld(), player, type, variant, posX, posY, posZ
    );
  }

  @Override
  public @NotNull Material typeAt(int posX, int posY, int posZ) {
    return VolatileBlockAccess.blockAccess(player.getWorld(), posX, posY, posZ).getType();
  }

  @Override
  public int variantIndexAt(int posX, int posY, int posZ) {
    return BlockAccess.global().variantIndexOf(VolatileBlockAccess.blockAccess(player.getWorld(), posX, posY, posZ));
  }

  @Override
  public void invalidateAll() {

  }

  @Override
  public void invalidateCache() {

  }

  @Override
  public void invalidateCacheAt0(int posX, int posY, int posZ) {

  }

  @Override
  public boolean currentlyInOverride(int posX, int posY, int posZ) {
    return false;
  }

  @Override
  public BlockState overrideOf(int posX, int posY, int posZ) {
    return BlockState.empty();
  }

  @Override
  public void lockOverride(int posX, int posY, int posZ) {

  }

  @Override
  public void unlockOverride(int posX, int posY, int posZ) {

  }

  @Override
  public void invalidateOverride(int posX, int posY, int posZ) {

  }

  @Override
  public int numOfIndexedReplacements() {
    return 0;
  }

  @Override
  public int numOfLocatedReplacements() {
    return 0;
  }

  @Override
  public void override(World world, int posX, int posY, int posZ, Material type, int variant) {

  }

  @Override
  public void invalidateOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {

  }
}
