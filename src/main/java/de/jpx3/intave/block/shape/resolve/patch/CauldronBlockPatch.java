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

import com.google.common.collect.Lists;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Locale;

final class CauldronBlockPatch extends BlockShapePatch {
  private static final BlockShape shape8;
  private static final BlockShape shape13;

  static {
    float wallWidth = 2f /*/ 16f*/;
    shape8 = BlockShapes.optimizedMerge(Lists.newArrayList(
      BoundingBox.originFromX16(0.0f, 0.0F, 0.0F, 16.0F, 5.0f, 16.0F),
      BoundingBox.originFromX16(0.0F, 0.0F, 0.0F, wallWidth, 16.0F, 16.0F),
      BoundingBox.originFromX16(0.0F, 0.0F, 0.0F, 16.0F, 16.0F, wallWidth),
      BoundingBox.originFromX16(16.0F - wallWidth, 0.0F, 0.0F, 16.0F, 16.0F, 16.0F),
      BoundingBox.originFromX16(0.0F, 0.0F, 16.0F - wallWidth, 16.0F, 16.0F, 16.0F)
    ));
    shape13 = BlockShapes.optimizedMerge(Lists.newArrayList(
      BoundingBox.originFromX16(0.0F, 0.0F, 0.0F, 16.0F, 4.0f, 16.0F),
      BoundingBox.originFromX16(0.0F, 0.0F, 0.0F, wallWidth, 16.0F, 16.0F),
      BoundingBox.originFromX16(0.0F, 0.0F, 0.0F, 16.0F, 16.0F, wallWidth),
      BoundingBox.originFromX16(16.0F - wallWidth, 0.0F, 0.0F, 16.0F, 16.0F, 16.0F),
      BoundingBox.originFromX16(0.0F, 0.0F, 16.0F - wallWidth, 16.0F, 16.0F, 16.0F)
    ));
  }

  private static final boolean SERVER_IS_1_13 = MinecraftVersions.VER1_13_0.atOrAbove();

  @Override
  protected BlockShape collisionPatch(World world, User user, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    if (user.meta().protocol().aquaticUpdate()) {
      if (SERVER_IS_1_13) {
        return shape;
      } else {
        return shape13;
      }
    } else {
      if (SERVER_IS_1_13) {
        return shape8;
      } else {
        return shape;
      }
    }
  }

  @Override
  public boolean appliesTo(Material material) {
    return material.name().toLowerCase(Locale.ROOT).contains("cauldron");
  }
}
