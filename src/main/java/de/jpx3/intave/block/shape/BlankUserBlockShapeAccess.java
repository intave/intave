package de.jpx3.intave.block.shape;

import de.jpx3.intave.shade.BoundingBox;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class BlankUserBlockShapeAccess implements BlockShapeAccess {
  @Override
  public @NotNull List<BoundingBox> resolveBoxes(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Material resolveType(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    return Material.AIR;
  }

  @Override
  public int resolveVariant(int chunkX, int chunkZ, int posX, int posY, int posZ) {
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
