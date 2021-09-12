package de.jpx3.intave.block.variant;

import de.jpx3.intave.user.User;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

public final class BlockVariant {
  public BlockVariant(List<BlockVariantData<?>> blockStates) {
    for (BlockVariantData<?> blockState : blockStates) {
      blockState.build();
    }
  }

  public <T> T valueOf(User user, Block block, BlockVariantData<T> blockVariantData) {
    return blockVariantData.value(user, block);
  }

  public static Builder builder() {
    return new Builder();
  }

  public final static class Builder {
    private final List<BlockVariantData<?>> blockStates = new ArrayList<>();

    private Builder() {
    }

    public Builder with(BlockVariantData<?> retriever) {
      this.blockStates.add(retriever);
      return this;
    }

    public BlockVariant build() {
      return new BlockVariant(blockStates);
    }
  }
}