/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.BlockState;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

final class DoorBlockPatch extends BlockShapePatch {
  private static final float THICKNESS = 0.1875F;
  private static final int HALF_BIT = 8;
  private static final int HINGE_BIT = 1;

  @Override
  protected BlockShape collisionPatch(
    World world, User user,
    int posX, int posY, int posZ,
    Material type, int blockState,
    BlockShape shape
  ) {
    return resolveDoorShape(user, posX, posY, posZ, type, blockState);
  }

  @Override
  protected BlockShape outlinePatch(
    World world, User user,
    int posX, int posY, int posZ,
    Material type, int blockState,
    BlockShape shape
  ) {
    return resolveDoorShape(user, posX, posY, posZ, type, blockState);
  }

  private BlockShape resolveDoorShape(
    User user,
    int posX, int posY, int posZ,
    Material type, int blockState
  ) {
    BlockCache blockCache = user.blockCache();
    boolean upperHalf = (blockState & HALF_BIT) != 0;
    int otherY = upperHalf ? posY - 1 : posY + 1;
    BlockState otherHalf = blockCache.peekStateAt(posX, otherY, posZ);
    int lowerVariant = upperHalf ? 0 : blockState;
    int upperVariant = upperHalf ? blockState
      : HALF_BIT | (user.meta().protocol().aquaticUpdate() ? HINGE_BIT : 0);
    if (otherHalf != null && otherHalf.type() == type) {
      int otherVariant = otherHalf.variantIndex();
      if (upperHalf && (otherVariant & HALF_BIT) == 0) {
        lowerVariant = otherVariant;
      } else if (!upperHalf && (otherVariant & HALF_BIT) != 0) {
        upperVariant = otherVariant;
      }
    }

    Direction facing = Direction.getHorizontal(lowerVariant & 3).rotateYCCW();
    boolean open = (lowerVariant & 4) != 0;
    boolean hingeRight = (upperVariant & HINGE_BIT) != 0;
    BoundingBoxBuilder builder = BoundingBoxBuilder.create();
    if (!open) {
      closedShape(builder, facing);
    } else {
      openShape(builder, facing, hingeRight);
    }
    return builder.applyAndResolveAsShape();
  }

  private static void closedShape(BoundingBoxBuilder builder, Direction facing) {
    switch (facing) {
      case EAST:
        builder.shape(0.0F, 0.0F, 0.0F, THICKNESS, 1.0F, 1.0F);
        break;
      case SOUTH:
        builder.shape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, THICKNESS);
        break;
      case WEST:
        builder.shape(1.0F - THICKNESS, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        break;
      case NORTH:
        builder.shape(0.0F, 0.0F, 1.0F - THICKNESS, 1.0F, 1.0F, 1.0F);
        break;
      default:
        throw new IllegalStateException("Door cannot face " + facing);
    }
  }

  private static void openShape(BoundingBoxBuilder builder, Direction facing, boolean hingeRight) {
    switch (facing) {
      case EAST:
        if (hingeRight) {
          builder.shape(0.0F, 0.0F, 1.0F - THICKNESS, 1.0F, 1.0F, 1.0F);
        } else {
          builder.shape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, THICKNESS);
        }
        break;
      case SOUTH:
        if (hingeRight) {
          builder.shape(0.0F, 0.0F, 0.0F, THICKNESS, 1.0F, 1.0F);
        } else {
          builder.shape(1.0F - THICKNESS, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        }
        break;
      case WEST:
        if (hingeRight) {
          builder.shape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, THICKNESS);
        } else {
          builder.shape(0.0F, 0.0F, 1.0F - THICKNESS, 1.0F, 1.0F, 1.0F);
        }
        break;
      case NORTH:
        if (hingeRight) {
          builder.shape(1.0F - THICKNESS, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        } else {
          builder.shape(0.0F, 0.0F, 0.0F, THICKNESS, 1.0F, 1.0F);
        }
        break;
      default:
        throw new IllegalStateException("Door cannot face " + facing);
    }
  }

  @Override
  protected boolean appliesTo(Material material) {
    String name = material.name();
    return !MinecraftVersions.VER1_13_0.atOrAbove()
      && material.isBlock()
      && name.contains("DOOR")
      && !name.contains("TRAPDOOR")
      && !name.contains("TRAP_DOOR");
  }
}
