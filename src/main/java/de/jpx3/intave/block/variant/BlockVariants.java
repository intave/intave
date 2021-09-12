package de.jpx3.intave.block.variant;

import java.util.ArrayList;
import java.util.List;

public final class BlockVariants {
  private final static List<BlockVariantData<?>> blockStates = new ArrayList<>();

  public static void setup(BlockVariantData<?> blockState) {
    blockStates.add(blockState);
  }

  public BlockVariantData<?> ofNativeState(Object nativeBlockState) {
    return null;
  }
}
