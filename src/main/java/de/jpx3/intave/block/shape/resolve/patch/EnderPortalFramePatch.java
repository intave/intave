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

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

final class EnderPortalFramePatch extends BlockShapePatch {
  private final BoundingBox baseShape = BoundingBox.originFromX16(0, 0, 0, 16, 13, 16);
  private final BoundingBox eye8 = BoundingBox.originFromX16(5, 13, 5, 11, 16, 11);
  private final BoundingBox eye13 = BoundingBox.originFromX16(4, 13, 4, 12, 16, 12);

  @Override
  protected BlockShape collisionPatch(World world, User user, int posX, int posY, int posZ, Material type, int variantIndex, BlockShape shape) {
    return patch(user, type, variantIndex);
  }

  @Override
  protected BlockShape outlinePatch(World world, User user, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    return patch(user, type, blockState);
  }

  private BlockShape patch(User user, Material type, int blockState) {
    BlockVariant variant = BlockVariantRegister.variantOf(type, blockState);
    boolean eye = variant.propertyOf("eye");
    List<BoundingBox> boundingBoxes = new ArrayList<>();
    boundingBoxes.add(baseShape);
    if (eye) {
      if (user.meta().protocol().aquaticUpdate()) {
        boundingBoxes.add(eye13);
      } else {
        boundingBoxes.add(eye8);
      }
    }
    return BlockShapes.merge(boundingBoxes);
  }

  @Override
  public boolean appliesTo(Material material) {
    return material.name().endsWith("PORTAL_FRAME");
  }
}
