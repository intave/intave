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

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.BlockCacheEntry;
import de.jpx3.intave.share.PositionedBlockState;
import de.jpx3.intave.user.User;
import org.bukkit.Material;

import java.util.*;

final class DoorInteractionInteractionPatch implements BlockInteractionPatch {
  private static final int LEGACY_HALF_BIT = 8;
  private static final int LEGACY_OPEN_BIT = 4;
  private static final boolean LEGACY_VARIANTS = !MinecraftVersions.VER1_13_0.atOrAbove();
  private static final Set<Material> DOOR_BLOCKS = MaterialSearch.materialsThatContain("DOOR");

  @Override
  public boolean matches(Material material) {
    return isDoor(material);
  }

  @Override
  public List<PositionedBlockState> interact(
    User user, BlockCache blockCache,
    int blockX, int blockY, int blockZ,
    Material material, int variant
  ) {
    if (!canOpenByHand(material)) {
      return Collections.emptyList();
    }
    return LEGACY_VARIANTS
      ? interactLegacy(blockCache, blockX, blockY, blockZ, material, variant)
      : interactModern(blockCache, blockX, blockY, blockZ, material, variant);
  }

  static boolean isDoor(Material material) {
    String name = material.name();
    return material.isBlock()
      && DOOR_BLOCKS.contains(material)
      && !name.contains("TRAPDOOR")
      && !name.contains("TRAP_DOOR");
  }

  static boolean canOpenByHand(Material material) {
    return !material.name().contains("IRON");
  }

  private List<PositionedBlockState> interactLegacy(
    BlockCache blockCache,
    int blockX, int blockY, int blockZ,
    Material material, int variant
  ) {
    boolean clickedUpper = (variant & LEGACY_HALF_BIT) != 0;
    int lowerY = clickedUpper ? blockY - 1 : blockY;
    int upperY = lowerY + 1;
    BlockCacheEntry lower = blockCache.stateAt(blockX, lowerY, blockZ);
    BlockCacheEntry upper = blockCache.stateAt(blockX, upperY, blockZ);
    boolean validLower = lower.type() == material
      && (lower.variantIndex() & LEGACY_HALF_BIT) == 0;
    boolean validUpper = upper.type() == material
      && (upper.variantIndex() & LEGACY_HALF_BIT) != 0;
    if (!validLower || (clickedUpper && !validUpper)) {
      return Collections.emptyList();
    }

    int openLowerVariant = lower.variantIndex() ^ LEGACY_OPEN_BIT;
    if (!BlockVariantRegister.variantIdsOf(material).contains(openLowerVariant)) {
      return Collections.emptyList();
    }
    PositionedBlockState lowerMutation = BlockInteractionPatch.positionedBlockState(
      blockX, lowerY, blockZ,
      material, openLowerVariant
    );
    if (!validUpper) {
      return Collections.singletonList(lowerMutation);
    }
    return Arrays.asList(
      lowerMutation,
      BlockInteractionPatch.positionedBlockState(
        blockX, upperY, blockZ,
        material, upper.variantIndex()
      )
    );
  }

  private List<PositionedBlockState> interactModern(
    BlockCache blockCache,
    int blockX, int blockY, int blockZ,
    Material material, int variant
  ) {
    Map<String, Comparable<?>> clickedProperties =
      BlockVariantRegister.propertiesOf(material, variant);
    boolean clickedUpper;
    if (isHalf(clickedProperties, "upper")) {
      clickedUpper = true;
    } else if (isHalf(clickedProperties, "lower")) {
      clickedUpper = false;
    } else {
      return Collections.emptyList();
    }

    int lowerY = clickedUpper ? blockY - 1 : blockY;
    int upperY = lowerY + 1;
    BlockCacheEntry lower = blockCache.stateAt(blockX, lowerY, blockZ);
    BlockCacheEntry upper = blockCache.stateAt(blockX, upperY, blockZ);
    if (!validPair(material, lower, upper)) {
      return interactModernBlock(blockX, blockY, blockZ, material, variant);
    }

    Map<String, Comparable<?>> lowerProperties =
      BlockVariantRegister.propertiesOf(material, lower.variantIndex());
    Map<String, Comparable<?>> upperProperties =
      BlockVariantRegister.propertiesOf(material, upper.variantIndex());
    Comparable<?> lowerOpen = lowerProperties.get("open");
    Comparable<?> upperOpen = upperProperties.get("open");
    if (!isHalf(lowerProperties, "lower")
      || !isHalf(upperProperties, "upper")
      || !(lowerOpen instanceof Boolean)
      || !lowerOpen.equals(upperOpen)) {
      return interactModernBlock(blockX, blockY, blockZ, material, variant);
    }

    boolean newOpen = !((Boolean) lowerOpen);
    int newLowerVariant = BlockInteractionPatch.setBooleanProperty(
      material, lowerProperties, "open", newOpen
    );
    int newUpperVariant = BlockInteractionPatch.setBooleanProperty(
      material, upperProperties, "open", newOpen
    );
    if (newLowerVariant == NO_CHANGE || newUpperVariant == NO_CHANGE) {
      return Collections.emptyList();
    }
    return Arrays.asList(
      BlockInteractionPatch.positionedBlockState(
        blockX, lowerY, blockZ,
        material, newLowerVariant
      ),
      BlockInteractionPatch.positionedBlockState(
        blockX, upperY, blockZ,
        material, newUpperVariant
      )
    );
  }

  private List<PositionedBlockState> interactModernBlock(
    int blockX, int blockY, int blockZ,
    Material material, int variant
  ) {
    int newVariant = BlockInteractionPatch.toggleBooleanProperty(material, variant, "open");
    if (newVariant == NO_CHANGE) {
      return Collections.emptyList();
    }
    return Collections.singletonList(BlockInteractionPatch.positionedBlockState(
      blockX, blockY, blockZ, material, newVariant
    ));
  }

  private static boolean validPair(Material material, BlockCacheEntry lower, BlockCacheEntry upper) {
    return lower.type() == material && upper.type() == material;
  }

  private static boolean isHalf(Map<String, Comparable<?>> properties, String expected) {
    Comparable<?> half = properties.get("half");
    return half != null && expected.equalsIgnoreCase(String.valueOf(half));
  }
}
