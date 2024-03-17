package de.jpx3.intave.block.shape.resolve.patch;

import com.google.common.collect.Lists;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.share.BoundingBox;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.server.v1_12_R1.BlockStairs.*;

class StairPatch extends BlockShapePatch {
  private static final boolean AQUATIC_UPDATE = MinecraftVersions.VER1_13_0.atOrAbove();
  private static final boolean COLOR_UPDATE = MinecraftVersions.VER1_12_0.atOrAbove();

  private static final BoundingBox AABB_SLAB_TOP = BoundingBox.originFrom(0.0D, 0.5D, 0.0D, 1.0D, 1.0D, 1.0D);
  private static final BoundingBox AABB_QTR_TOP_WEST = BoundingBox.originFrom(0.0D, 0.5D, 0.0D, 0.5D, 1.0D, 1.0D);
  private static final BoundingBox AABB_QTR_TOP_EAST = BoundingBox.originFrom(0.5D, 0.5D, 0.0D, 1.0D, 1.0D, 1.0D);
  private static final BoundingBox AABB_QTR_TOP_NORTH = BoundingBox.originFrom(0.0D, 0.5D, 0.0D, 1.0D, 1.0D, 0.5D);
  private static final BoundingBox AABB_QTR_TOP_SOUTH = BoundingBox.originFrom(0.0D, 0.5D, 0.5D, 1.0D, 1.0D, 1.0D);
  private static final BoundingBox AABB_OCT_TOP_NW = BoundingBox.originFrom(0.0D, 0.5D, 0.0D, 0.5D, 1.0D, 0.5D);
  private static final BoundingBox AABB_OCT_TOP_NE =
    BoundingBox.originFrom(0.5D, 0.5D, 0.0D, 1.0D, 1.0D, 0.5D);
  private static final BoundingBox AABB_OCT_TOP_SW =
    BoundingBox.originFrom(0.0D, 0.5D, 0.5D, 0.5D, 1.0D, 1.0D);
  private static final BoundingBox AABB_OCT_TOP_SE =
    BoundingBox.originFrom(0.5D, 0.5D, 0.5D, 1.0D, 1.0D, 1.0D);
  private static final BoundingBox AABB_SLAB_BOTTOM =
    BoundingBox.originFrom(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D);
  private static final BoundingBox AABB_QTR_BOT_WEST =
    BoundingBox.originFrom(0.0D, 0.0D, 0.0D, 0.5D, 0.5D, 1.0D);
  private static final BoundingBox AABB_QTR_BOT_EAST =
    BoundingBox.originFrom(0.5D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D);
  private static final BoundingBox AABB_QTR_BOT_NORTH =
    BoundingBox.originFrom(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 0.5D);
  private static final BoundingBox AABB_QTR_BOT_SOUTH =
    BoundingBox.originFrom(0.0D, 0.0D, 0.5D, 1.0D, 0.5D, 1.0D);
  private static final BoundingBox AABB_OCT_BOT_NW =
    BoundingBox.originFrom(0.0D, 0.0D, 0.0D, 0.5D, 0.5D, 0.5D);
  private static final BoundingBox AABB_OCT_BOT_NE =
    BoundingBox.originFrom(0.5D, 0.0D, 0.0D, 1.0D, 0.5D, 0.5D);
  private static final BoundingBox AABB_OCT_BOT_SW =
    BoundingBox.originFrom(0.0D, 0.0D, 0.5D, 0.5D, 0.5D, 1.0D);
  private static final BoundingBox AABB_OCT_BOT_SE =
    BoundingBox.originFrom(0.5D, 0.0D, 0.5D, 1.0D, 0.5D, 1.0D);

  @Override
  protected BlockShape collisionPatch(
    org.bukkit.World world,
    Player player,
    int posX,
    int posY,
    int posZ,
    Material type,
    int variantIndex,
    BlockShape shape
  ) {
    // Only apply on 1.12.X
    if (!COLOR_UPDATE || AQUATIC_UPDATE) {
      return shape;
    }
    org.bukkit.block.Block bukkitBlock = VolatileBlockAccess.blockAccess(world, posX, posY, posZ);
    IBlockData data = (IBlockData) BlockVariantNativeAccess.nativeVariantAccess(bukkitBlock);
    Block block = data.getBlock();
    // Only apply if stairs are really at this position
    if (block instanceof BlockStairs) {
      World serverWorld = ((CraftWorld) world).getHandle();
      BlockPosition pos = new BlockPosition(posX, posY, posZ);
      BlockStairs stairs = (BlockStairs) block;
      List<BoundingBox> shapes = calculateCollisionShapes(serverWorld, stairs.getBlockData(), variantIndex, pos);
      // Contextualize corrected collision boxes to position
      List<BlockShape> contextualizedShapes = new ArrayList<>();
      for (BoundingBox boundingBox : shapes) {
        contextualizedShapes.add(boundingBox.contextualized(posX, posY, posZ));
      }
      return BlockShapes.merge(contextualizedShapes);
    }
    return shape;
  }

  private List<BoundingBox> calculateCollisionShapes(World world, IBlockData data, int state, BlockPosition pos) {
    List<BoundingBox> shapes = Lists.newArrayList();
    BlockStairs block = (BlockStairs) data.getBlock();
    // Override stair data as only the legacy one contains correct properties
    // meh meh meh
    data = block.fromLegacyData(state);
    boolean top = data.get(HALF) == BlockStairs.EnumHalf.TOP;
    shapes.add(top ? AABB_SLAB_TOP : AABB_SLAB_BOTTOM);
    // Calculate the stair shape as the one in the BlockData is wrong
    BlockStairs.EnumStairShape stairShape = calculateStairShape(data, world, pos);
    // Inner and Normal stairs
    if (stairShape == BlockStairs.EnumStairShape.STRAIGHT
      || stairShape == BlockStairs.EnumStairShape.INNER_LEFT
      || stairShape == BlockStairs.EnumStairShape.INNER_RIGHT) {
      shapes.add(collQuarterBlock(data));
    }
    // 3 quarter stairs (outer stairs)
    if (stairShape != BlockStairs.EnumStairShape.STRAIGHT) {
      shapes.add(collEightBlock(data, stairShape));
    }
    return shapes;
  }

  private EnumStairShape calculateStairShape(IBlockData nativeData, IBlockAccess access, BlockPosition pos) {
    EnumDirection nativeFace = nativeData.get(FACING);
    // Check for outer shape
    IBlockData data = access.getType(pos.shift(nativeFace));
    if (x(data) && nativeData.get(HALF) == data.get(HALF)) {
      EnumDirection direction = data.get(FACING);
      if (direction.k() != nativeData.get(FACING).k() && differentFace(nativeData, access, pos, direction.opposite())) {
        if (direction == nativeFace.f()) {
          return BlockStairs.EnumStairShape.OUTER_LEFT;
        }
        return BlockStairs.EnumStairShape.OUTER_RIGHT;
      }
    }
    // Check for inner or normal shape
    IBlockData shiftedFaceData = access.getType(pos.shift(nativeFace.opposite()));
    if (x(shiftedFaceData) && nativeData.get(HALF) == shiftedFaceData.get(HALF)) {
      EnumDirection direction = shiftedFaceData.get(FACING);
      if (direction.k() != nativeData.get(FACING).k() && differentFace(nativeData, access, pos, direction)) {
        if (direction == nativeFace.f()) {
          return BlockStairs.EnumStairShape.INNER_LEFT;
        }
        return BlockStairs.EnumStairShape.INNER_RIGHT;
      }
    }
    return BlockStairs.EnumStairShape.STRAIGHT;
  }

  private boolean differentFace(IBlockData nativeData, IBlockAccess access, BlockPosition pos, EnumDirection direction) {
    IBlockData shiftedFaceData = access.getType(pos.shift(direction));
    return !x(shiftedFaceData) || shiftedFaceData.get(FACING) != nativeData.get(FACING) || shiftedFaceData.get(HALF) != nativeData.get(HALF);
  }

  private BoundingBox collQuarterBlock(IBlockData data) {
    boolean top = data.get(HALF) == BlockStairs.EnumHalf.TOP;
    switch (data.get(FACING)) {
      case NORTH:
      default:
        return top ? AABB_QTR_BOT_NORTH : AABB_QTR_TOP_NORTH;
      case SOUTH:
        return top ? AABB_QTR_BOT_SOUTH : AABB_QTR_TOP_SOUTH;
      case WEST:
        return top ? AABB_QTR_BOT_WEST : AABB_QTR_TOP_WEST;
      case EAST:
        return top ? AABB_QTR_BOT_EAST : AABB_QTR_TOP_EAST;
    }
  }

  private static BoundingBox collEightBlock(IBlockData data, EnumStairShape shape) {
    EnumDirection nativeFace = data.get(FACING);
    EnumDirection actualBlockFace;
    switch (shape) {
      case OUTER_LEFT:
      default:
        actualBlockFace = nativeFace;
        break;
      case OUTER_RIGHT:
        actualBlockFace = nativeFace.e();
        break;
      case INNER_RIGHT:
        actualBlockFace = nativeFace.opposite();
        break;
      case INNER_LEFT:
        actualBlockFace = nativeFace.f();
    }
    boolean top = data.get(HALF) == BlockStairs.EnumHalf.TOP;
    switch (actualBlockFace) {
      case NORTH:
      default:
        return top ? AABB_OCT_BOT_NW : AABB_OCT_TOP_NW;
      case SOUTH:
        return top ? AABB_OCT_BOT_SE : AABB_OCT_TOP_SE;
      case WEST:
        return top ? AABB_OCT_BOT_SW : AABB_OCT_TOP_SW;
      case EAST:
        return top ? AABB_OCT_BOT_NE : AABB_OCT_TOP_NE;
    }
  }

  @Override
  public boolean appliesTo(Material material) {
    return material.name().endsWith("STAIRS");
  }
}
