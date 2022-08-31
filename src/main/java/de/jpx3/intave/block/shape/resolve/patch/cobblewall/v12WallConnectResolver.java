package de.jpx3.intave.block.shape.resolve.patch.cobblewall;

import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.klass.rewrite.PatchyTranslateParameters;
import de.jpx3.intave.share.Direction;
import net.minecraft.server.v1_12_R1.*;

@PatchyAutoTranslation
public class v12WallConnectResolver implements WallConnectResolver {
  @Override
  @PatchyAutoTranslation
  public boolean canConnectTo(
    org.bukkit.World world, de.jpx3.intave.share.BlockPosition position, Direction direction
  ) {
    World worldIn = ((org.bukkit.craftbukkit.v1_12_R1.CraftWorld) world).getHandle();
    BlockPosition pos = new BlockPosition(position.xCoord, position.yCoord, position.zCoord);
    IBlockData data = worldIn.getType(pos);
    Block block = data.getBlock();
    EnumBlockFaceShape shape = data.d(worldIn, pos, EnumDirection.values()[direction.ordinal()]);
    // Stairs are wrongly given back by the server, don't ask me why, prob. some error which was
    // introduced. Also, only persistent on cobble walls.
    if (block instanceof BlockStairs) {
      shape = modifyStairShape((BlockStairs) block, worldIn, data, pos, EnumDirection.values()[direction.ordinal()]);
    }
    boolean whitelistedShape = shape == EnumBlockFaceShape.MIDDLE_POLE_THICK
      || shape == EnumBlockFaceShape.MIDDLE_POLE && block instanceof BlockFenceGate;
    return !invalidBlock(block) && shape == EnumBlockFaceShape.SOLID || whitelistedShape;
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private EnumBlockFaceShape modifyStairShape(
    BlockStairs block,
    IBlockAccess worldIn,
    IBlockData data,
    BlockPosition position,
    EnumDirection direction
  ) {
    data = block.updateState(data, worldIn, position);
    if (direction.k() == EnumDirection.EnumAxis.Y) {
      return direction == EnumDirection.UP == (data.get(BlockStairs.HALF) == BlockStairs.EnumHalf.TOP)
        ? EnumBlockFaceShape.SOLID : EnumBlockFaceShape.UNDEFINED;
    } else {
      BlockStairs.EnumStairShape stairShape = data.get(BlockStairs.SHAPE);
      if (stairShape != BlockStairs.EnumStairShape.OUTER_LEFT
        && stairShape != BlockStairs.EnumStairShape.OUTER_RIGHT
      ) {
        EnumDirection blockDirection = data.get(BlockStairs.FACING);
        boolean parallelDirection = blockDirection.k() == direction.k() && blockDirection.c() != direction.c();
        switch (stairShape) {
          case INNER_RIGHT: {
            boolean sideParallelDirection = blockDirection.k() == direction.f().k() && blockDirection.c() != direction.f().c();
            return parallelDirection && sideParallelDirection
              ? EnumBlockFaceShape.SOLID : EnumBlockFaceShape.UNDEFINED;
          }
          case INNER_LEFT: {
            boolean sideParallelDirection = blockDirection.k() == direction.e().k() && blockDirection.c() != direction.e().c();
            return parallelDirection && sideParallelDirection
              ? EnumBlockFaceShape.SOLID : EnumBlockFaceShape.UNDEFINED;
          }
          case STRAIGHT: {
            return parallelDirection ? EnumBlockFaceShape.SOLID : EnumBlockFaceShape.UNDEFINED;
          }
          default:
            return EnumBlockFaceShape.UNDEFINED;
        }
      } else {
        return EnumBlockFaceShape.UNDEFINED;
      }
    }
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private boolean invalidBlock(Block block) {
    return pistonBlock(block)
      || block instanceof BlockPumpkin
      || block instanceof BlockMelon
      || block == Blocks.BARRIER;
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private boolean opaqueBlock(Block block) {
    return block instanceof BlockShulkerBox
      || block instanceof BlockLeaves
      || block instanceof BlockTrapdoor
      || block instanceof BlockBeacon
      || block instanceof BlockCauldron
      || block instanceof BlockStainedGlass
      || block == Blocks.GLASS
      || block == Blocks.GLOWSTONE
      || block == Blocks.ICE
      || block == Blocks.SEA_LANTERN;
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private boolean pistonBlock(Block block) {
    return opaqueBlock(block) || block instanceof BlockPiston;
  }
}
