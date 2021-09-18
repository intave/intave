package de.jpx3.intave.block.state;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

public final class EmptyBlockStateAccess implements BlockStateAccess {
  @Override
  public @NotNull BlockShape resolveShape(int posX, int posY, int posZ) {
    return BlockShapes.empty();
  }

  @Override
  public @NotNull Material resolveType(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    return Material.AIR;
  }

  @Override
  public int resolveVariantIndex(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    return 0;
  }

  @Override
  public void identityInvalidate() {
  }

  @Override
  public void invalidate() {
  }

  @Override
  public void invalidate0(int posX, int posY, int posZ) {
  }

  @Override
  public void override(World world, int posX, int posY, int posZ, Material type, int variant) {
  }

  @Override
  public void invalidateOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
  }

  @Override
  public boolean currentlyInOverride(int posX, int posY, int posZ) {
    return false;
  }

  @Override
  public BlockState overrideOf(int posX, int posY, int posZ) {
    return null;
  }

  @Override
  public void invalidateOverride(int posX, int posY, int posZ) {
  }

  @Override
  public Map<Location, BlockState> locatedReplacements() {
    return Collections.emptyMap();
  }

  @Override
  public Map<Long, BlockState> indexedReplacements() {
    return Collections.emptyMap();
  }
}
