package de.jpx3.intave.block.shape.resolve.patch.cobblewall;

import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.klass.rewrite.PatchyTranslateParameters;
import de.jpx3.intave.share.Direction;
import net.minecraft.server.v1_13_R2.*;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;

import static net.minecraft.server.v1_13_R2.BlockStairs.HALF;
import static net.minecraft.server.v1_13_R2.BlockStairs.SHAPE;

@PatchyAutoTranslation
public class v13WallConnectResolver implements WallConnectResolver {
  @Override
  @PatchyAutoTranslation
  public boolean canConnectTo(
    org.bukkit.World world, de.jpx3.intave.share.BlockPosition position, Direction direction) {
    World worldIn = ((CraftWorld) world).getHandle();
    BlockPosition pos = new BlockPosition(position.xCoord, position.yCoord, position.zCoord);
    IBlockData data = worldIn.getType(pos);
    Block block = data.getBlock();
    EnumBlockFaceShape shape = data.c(worldIn, pos, EnumDirection.values()[direction.ordinal()]);
    // Stairs are wrongly given back by the server, don't ask me why, prob. some error which was
    // introduced. Also, only persistent on cobble walls.
    if (block instanceof BlockStairs) {
      shape = modifyStairShape(data, EnumDirection.values()[direction.ordinal()]);
    }
    boolean whitelistedShape =
      shape == EnumBlockFaceShape.MIDDLE_POLE_THICK
        || shape == EnumBlockFaceShape.MIDDLE_POLE && block instanceof BlockFenceGate;
    return !BlockCobbleWall.f(block) && shape == EnumBlockFaceShape.SOLID || whitelistedShape;
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  public EnumBlockFaceShape modifyStairShape(IBlockData data, EnumDirection direction) {
    if (direction.k() == EnumDirection.EnumAxis.Y) {
      return direction == EnumDirection.UP == (data.get(HALF) == BlockPropertyHalf.TOP)
        ? EnumBlockFaceShape.SOLID
        : EnumBlockFaceShape.UNDEFINED;
    } else {
      BlockPropertyStairsShape stairsShape = data.get(SHAPE);
      if (stairsShape != BlockPropertyStairsShape.OUTER_LEFT
        && stairsShape != BlockPropertyStairsShape.OUTER_RIGHT) {
        EnumDirection blockDirection = data.get(BlockStairs.FACING);
        boolean parallelDirection =
          blockDirection.k() == direction.k() && blockDirection.c() != direction.c();
        switch (stairsShape) {
          case STRAIGHT: {
            return parallelDirection ? EnumBlockFaceShape.SOLID : EnumBlockFaceShape.UNDEFINED;
          }
          case INNER_LEFT: {
            boolean sideParallelDirection =
              blockDirection.k() == direction.e().k()
                && blockDirection.c() != direction.e().c();
            return parallelDirection && sideParallelDirection
              ? EnumBlockFaceShape.SOLID
              : EnumBlockFaceShape.UNDEFINED;
          }
          case INNER_RIGHT: {
            boolean sideParallelDirection =
              blockDirection.k() == direction.f().k()
                && blockDirection.c() != direction.f().c();
            return parallelDirection && sideParallelDirection
              ? EnumBlockFaceShape.SOLID
              : EnumBlockFaceShape.UNDEFINED;
          }
          default: {
            return EnumBlockFaceShape.UNDEFINED;
          }
        }
      } else {
        return EnumBlockFaceShape.UNDEFINED;
      }
    }
  }
}
