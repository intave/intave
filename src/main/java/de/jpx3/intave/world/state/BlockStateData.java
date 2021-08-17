package de.jpx3.intave.world.state;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import net.minecraft.server.v1_16_R3.BlockStateBoolean;
import net.minecraft.server.v1_16_R3.BlockStateInteger;
import net.minecraft.server.v1_16_R3.IBlockData;
import net.minecraft.server.v1_16_R3.IBlockState;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_16_R3.block.CraftBlock;
import org.bukkit.craftbukkit.v1_16_R3.block.data.CraftBlockData;

public abstract class BlockStateData<T> {
  private final String name;
  private final T defaultValue;
  private final Class<T> clazz;

  protected BlockStateData(String name, T defaultValue, Class<T> clazz) {
    this.name = name;
    this.defaultValue = defaultValue;
    this.clazz = clazz;
  }

  public abstract void build();

  public abstract Object convert();

  public T value(Block block) {
    return BlockStateServerBridge.valueOf(block, this);
  }

  public String name() {
    return name;
  }

  @PatchyAutoTranslation
  public static final class BlockStateServerBridge {
    private final static boolean INTERFACE_RESOLVE = !MinecraftVersions.VER1_14_0.atOrAbove();

    public static <T> T valueOf(Block block, BlockStateData<T> blockStateData) {
      return INTERFACE_RESOLVE ? invokeInterfaceResolve(block, blockStateData) : invokeSpecialResolve(block, blockStateData);
    }

    @PatchyAutoTranslation
    private static <T> T invokeSpecialResolve(Block block, BlockStateData<T> blockStateData) {
      CraftBlock craftBlock = (CraftBlock) block;
      CraftBlockData craftBlockData = (CraftBlockData) craftBlock.getBlockData();
      IBlockData state = craftBlockData.getState();
      IBlockState<?> blockState = (IBlockState<?>) blockStateData.convert();
      // containsKey
      if (state.b(blockState)) {
        //noinspection unchecked
        return (T) state.get(blockState);
      } else {
        return blockStateData.defaultValue;
      }
    }

    // Fixes an IncompatibleClassChangeError
    @PatchyAutoTranslation
    private static <T> T invokeInterfaceResolve(Block block, BlockStateData<T> blockStateData) {
      org.bukkit.craftbukkit.v1_13_R2.block.CraftBlock craftBlock = (org.bukkit.craftbukkit.v1_13_R2.block.CraftBlock) block;
      org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData craftBlockData = (org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData) craftBlock.getBlockData();
      net.minecraft.server.v1_13_R2.IBlockState<?> blockState = (net.minecraft.server.v1_13_R2.IBlockState<?>) blockStateData.convert();
      net.minecraft.server.v1_13_R2.IBlockData state = craftBlockData.getState();
      // containsKey
      if (state.b(blockState)) {
        //noinspection unchecked
        return (T) state.get(blockState);
      } else {
        return blockStateData.defaultValue;
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