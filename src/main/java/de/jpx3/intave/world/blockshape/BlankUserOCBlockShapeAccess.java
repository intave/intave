package de.jpx3.intave.world.blockshape;

import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class BlankUserOCBlockShapeAccess implements OCBlockShapeAccess {
  @Override
  public List<WrappedAxisAlignedBB> resolveBoxes(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    return Collections.emptyList();
  }

  @Override
  public List<WrappedAxisAlignedBB> constructBlock(World world, int posX, int posY, int posZ, Material type, int blockState) {
    return Collections.emptyList();
  }

  @Override
  public Material resolveType(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    return Material.AIR;
  }

  @Override
  public int resolveData(int chunkX, int chunkZ, int posX, int posY, int posZ) {
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
  public void override(World world, int posX, int posY, int posZ, Material type, int blockState) {
  }

  @Override
  public void invalidateOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
  }

  @Override
  public boolean currentlyInOverride(int posX, int posY, int posZ) {
    return false;
  }

  @Override
  public BlockShape overrideOf(int posX, int posY, int posZ) {
    return null;
  }

  @Override
  public void invalidateOverride(int posX, int posY, int posZ) {
  }

  @Override
  public Map<Location, BlockShape> locatedReplacements() {
    return Collections.emptyMap();
  }

  @Override
  public Map<Long, BlockShape> indexedReplacements() {
    return Collections.emptyMap();
  }
}
