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

package de.jpx3.intave.block.interact;

import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.BlockCacheEntry;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.PositionedBlockState;
import de.jpx3.intave.user.User;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.Map;

interface BlockInteractionPatch {
  int NO_CHANGE = -1;

  boolean matches(Material material);

  default int interact(User user, Material material, int variant) {
    return NO_CHANGE;
  }

  default List<PositionedBlockState> interact(
    User user, BlockCache blockCache,
    int blockX, int blockY, int blockZ,
    Material material, int variant
  ) {
    int newVariant = interact(user, material, variant);
    if (newVariant == NO_CHANGE) {
      return Collections.emptyList();
    }
    return Collections.singletonList(positionedBlockState(
      blockX, blockY, blockZ, material, newVariant
    ));
  }

  static PositionedBlockState positionedBlockState(
    int blockX, int blockY, int blockZ,
    Material material, int variant
  ) {
    BlockCacheEntry state = new BlockCacheEntry(
      BlockShapes.emptyShape(), BlockShapes.emptyShape(),
      material, variant
    );
    return new PositionedBlockState(BlockPosition.of(blockX, blockY, blockZ), state);
  }

  static int toggleBooleanProperty(Material material, int variant, String propertyName) {
    Map<String, Comparable<?>> newBlockProperties = BlockVariantRegister.propertiesOf(material, variant);
    Comparable<?> value = newBlockProperties.get(propertyName);
    if (!(value instanceof Boolean)) {
      return BlockInteractionPatch.NO_CHANGE;
    }
    return setBooleanProperty(material, newBlockProperties, propertyName, !((Boolean) value));
  }

  static int setBooleanProperty(Material material, int variant, String propertyName, boolean value) {
    Map<String, Comparable<?>> newBlockProperties = BlockVariantRegister.propertiesOf(material, variant);
    if (!(newBlockProperties.get(propertyName) instanceof Boolean)) {
      return BlockInteractionPatch.NO_CHANGE;
    }
    return setBooleanProperty(material, newBlockProperties, propertyName, value);
  }

  static int setBooleanProperty(
    Material material, Map<String, Comparable<?>> newBlockProperties,
    String propertyName, boolean value
  ) {
    newBlockProperties.put(propertyName, value);
    return BlockVariantRegister.variantIdOfProperties(material, newBlockProperties);
  }
}
