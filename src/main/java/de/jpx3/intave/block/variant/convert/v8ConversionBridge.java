package de.jpx3.intave.block.variant.convert;

import de.jpx3.intave.block.variant.*;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockStateBoolean;
import net.minecraft.server.v1_8_R3.BlockStateEnum;
import net.minecraft.server.v1_8_R3.BlockStateInteger;
import net.minecraft.server.v1_8_R3.BlockStateList;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.IBlockState;

import java.lang.reflect.Method;
import java.util.*;

@PatchyAutoTranslation
public final class v8ConversionBridge implements ConversionBridge {
  private static Method getStateListMethod;

  @PatchyAutoTranslation
  public Map<Setting<?>, Comparable<?>> settingsOf(Object blockData) {
    IBlockData data = (IBlockData) blockData;
    Block block = data.getBlock();
    Map<Setting<?>, Comparable<?>> configuration = new HashMap<>();
    try {
      if (getStateListMethod == null) {
        getStateListMethod = Lookup.serverClass("Block").getDeclaredMethod("getStateList");
        getStateListMethod.setAccessible(true);
      }
      //noinspection rawtypes
      Collection<IBlockState> states = ((BlockStateList) getStateListMethod.invoke(block)).d();
      if (states.isEmpty()) {
        return Collections.emptyMap();
      }
      for (IBlockState<?> state : states) {
        Setting<?> setting = SettingCache.computeSettingIfAbsent(state, this::convertSetting);
        configuration.put(setting, convertEnumToIndexIfPresent(data.get(state)));
      }
    } catch (Exception exception) {
      exception.printStackTrace();
    }
    return configuration;
  }

  @PatchyAutoTranslation
  public Setting<?> convertSetting(Object blockState) {
    IBlockState<?> state = (IBlockState<?>) blockState;
    String name = state.a();
    if (state instanceof BlockStateInteger) {
      BlockStateInteger blockStateInteger = (BlockStateInteger) state;
      Collection<Integer> values = blockStateInteger.c();
      IntSummaryStatistics statistics = values.stream().mapToInt(value -> value).summaryStatistics();
      return Settings.integerSetting(name, statistics.getMin(), statistics.getMax());
    } else if (state instanceof BlockStateBoolean) {
      return Settings.booleanSetting(name);
    } else if (state instanceof BlockStateEnum) {
      return Settings.enumSetting(name, state.b(), state.c());
    }
    throw new IllegalStateException("Unknown block state " + state + " (" + name + ")");
  }

  @PatchyAutoTranslation
  private static Comparable<?> convertEnumToIndexIfPresent(Comparable<?> initial) {
    if (initial.getClass().isEnum()) {
      return ((Enum<?>) initial).ordinal();
    }
    return initial;
  }
}
