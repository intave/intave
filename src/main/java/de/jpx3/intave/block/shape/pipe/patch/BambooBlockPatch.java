package de.jpx3.intave.block.shape.pipe.patch;

import de.jpx3.intave.block.access.BlockVariantAccess;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.WrappedMathHelper;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

final class BambooBlockPatch extends BoundingBoxPatch {
  private final static BoundingBox COLLISION_BOX = new BoundingBox(6.5D / 16.0, 0.0D, 6.5D / 16.0, 9.5D / 16.0, 1.0, 9.5D / 16.0);

  @Override
  public List<BoundingBox> patch(World world, Player player, Block block, List<BoundingBox> bbs) {
    return patch(world, player, block.getX(), block.getY(), block.getZ(), BlockTypeAccess.typeAccess(block, player), BlockVariantAccess.variantAccess(block), bbs);
  }

  @Override
  public List<BoundingBox> patch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<BoundingBox> bbs) {
    // Small Bamboo Leaves
    if (bbs.isEmpty()) {
      return bbs;
    }
    long randomCoordinate = WrappedMathHelper.coordinateRandom(posX, 0, posZ);
    double offsetX = ((double) ((float) (randomCoordinate & 15L) / 15.0F) - 0.5D) * 0.5D;
    double offsetZ = ((double) ((float) (randomCoordinate >> 8 & 15L) / 15.0F) - 0.5D) * 0.5D;
    double offsetY = 0.0;
    BoundingBoxBuilder builder = BoundingBoxBuilder.create();
    builder.shape(COLLISION_BOX.offset(offsetX, offsetY, offsetZ));
    return builder.applyAndResolve();
  }

  @Override
  public boolean appliesTo(Material material) {
    String name = material.name();
    return name.contains("BAMBOO");
  }
}