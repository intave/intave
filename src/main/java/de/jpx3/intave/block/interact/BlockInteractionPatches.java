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
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.BlockCacheEntry;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.PositionedBlockState;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.*;

public final class BlockInteractionPatches {
  private static final Map<Material, BlockInteractionPatch> patches = new EnumMap<>(Material.class);

  public static void setup() {
    patches.clear();
    add(TrapdoorInteractionInteractionPatch.class);
    add(FenceGateInteractionInteractionPatch.class);
    add(DoorInteractionInteractionPatch.class);
  }

  private static void add(Class<? extends BlockInteractionPatch> patchClass) {
    try {
      add(patchClass.newInstance());
    } catch (Exception | Error exception) {
      throw new IllegalStateException("Unable to load interactive block patch " + patchClass, exception);
    }
  }

  private static void add(BlockInteractionPatch patch) {
    for (Material material : Material.values()) {
      if (patch.matches(material)) {
        patches.put(material, patch);
      }
    }
  }

  public static int interact(User user, Material material, int variant) {
    BlockInteractionPatch patch = patches.get(material);
    return patch == null ? BlockInteractionPatch.NO_CHANGE : patch.interact(user, material, variant);
  }

  public static boolean interact(
    User user, World world, BlockCache blockCache,
    int blockX, int blockY, int blockZ,
    String reason
  ) {
    Material material = blockCache.typeAt(blockX, blockY, blockZ);
    int variant = blockCache.variantIndexAt(blockX, blockY, blockZ);
    BlockInteractionPatch patch = patches.get(material);
    if (patch == null) {
      return false;
    }

    List<PositionedBlockState> mutations = patch.interact(
      user, blockCache,
      blockX, blockY, blockZ,
      material, variant
    );
    if (!validMutations(mutations)) {
      return false;
    }
    for (PositionedBlockState mutation : mutations) {
	    blockCache.override(
        world, mutation, reason
      );
    }
    return true;
  }

  private static boolean validMutations(List<PositionedBlockState> mutations) {
    if (mutations == null || mutations.isEmpty()) {
      return false;
    }
    Set<BlockPosition> positions = new HashSet<>();
    for (PositionedBlockState mutation : mutations) {
      if (!validMutation(mutation)) {
        return false;
      }
      BlockPosition position = mutation.position();
      if (positions.contains(position)) {
        return false;
      }
      positions.add(position);
    }
    return true;
  }

  private static boolean validMutation(PositionedBlockState mutation) {
    if (mutation == null) {
      return false;
    }
    BlockPosition position = mutation.position();
    BlockCacheEntry state = mutation.state();
    return position != null
      && state != null
      && state.type() != null
      && state.variantIndex() != BlockInteractionPatch.NO_CHANGE
      && BlockVariantRegister.variantIdsOf(state.type()).contains(state.variantIndex());
  }
}
