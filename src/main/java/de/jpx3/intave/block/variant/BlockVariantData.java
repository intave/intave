package de.jpx3.intave.block.variant;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.BlockWrapper;
import de.jpx3.intave.clazz.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.user.User;
import net.minecraft.server.v1_16_R3.BlockStateBoolean;
import net.minecraft.server.v1_16_R3.BlockStateInteger;
import net.minecraft.server.v1_16_R3.IBlockData;
import net.minecraft.server.v1_16_R3.IBlockState;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_16_R3.block.CraftBlock;
import org.bukkit.craftbukkit.v1_16_R3.block.data.CraftBlockData;

public abstract class BlockVariantData<T> {
  private final String name;
  private final T defaultValue;
  private final Class<T> clazz;

  protected BlockVariantData(String name, T defaultValue, Class<T> clazz) {
    this.name = name;
    this.defaultValue = defaultValue;
    this.clazz = clazz;
  }

  public abstract void build();

  public abstract Object convert();

  public T value(User user, Block block) {
    return BlockStateServerBridge.valueOf(user, block, this);
  }

  public String name() {
    return name;
  }

  @PatchyAutoTranslation
  public static final class BlockStateServerBridge {
    private final static boolean INTERFACE_RESOLVE = MinecraftVersions.VER1_14_0.atOrAbove();

    public static <T> T valueOf(User user, Block block, BlockVariantData<T> blockVariantData) {
      return INTERFACE_RESOLVE ? invokeSpecialResolve(user, block, blockVariantData) : invokeInterfaceResolve(block, blockVariantData);
    }

    @PatchyAutoTranslation
    private static <T> T invokeSpecialResolve(User user, Block block, BlockVariantData<T> blockVariantData) {
      CraftBlock craftBlock = (CraftBlock) BlockWrapper.emit(user, block);
      CraftBlockData craftBlockData = (CraftBlockData) craftBlock.getBlockData();
      IBlockData state = craftBlockData.getState();
      IBlockState<?> blockState = (IBlockState<?>) blockVariantData.convert();
      // containsKey
      if (state.b(blockState)) {
        //noinspection unchecked
        return (T) state.get(blockState);
      } else {
        return blockVariantData.defaultValue;
      }
    }

    // Fixes an IncompatibleClassChangeError
    @PatchyAutoTranslation
    private static <T> T invokeInterfaceResolve(Block block, BlockVariantData<T> blockVariantData) {
      org.bukkit.craftbukkit.v1_13_R2.block.CraftBlock craftBlock = (org.bukkit.craftbukkit.v1_13_R2.block.CraftBlock) block;
      org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData craftBlockData = (org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData) craftBlock.getBlockData();
      net.minecraft.server.v1_13_R2.IBlockState<?> blockState = (net.minecraft.server.v1_13_R2.IBlockState<?>) blockVariantData.convert();
      net.minecraft.server.v1_13_R2.IBlockData state = craftBlockData.getState();
      // containsKey
      if (state.b(blockState)) {
        //noinspection unchecked
        return (T) state.get(blockState);
      } else {
        return blockVariantData.defaultValue;
      }
    }

    // Converter

    @PatchyAutoTranslation
    public static Object booleanStateOf(String name) {
      return BlockStateBoolean.of(name);
    }

    @PatchyAutoTranslation
    public static Object integerStateOf(String name, int min, int max) {
      return BlockStateInteger.of(name, min, max);
    }
  }
}