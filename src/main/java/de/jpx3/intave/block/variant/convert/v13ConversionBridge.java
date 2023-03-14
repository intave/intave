package de.jpx3.intave.block.variant.convert;

import de.jpx3.intave.block.variant.Setting;
import de.jpx3.intave.block.variant.Settings;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_13_R2.BlockStateBoolean;
import net.minecraft.server.v1_13_R2.BlockStateEnum;
import net.minecraft.server.v1_13_R2.BlockStateInteger;
import net.minecraft.server.v1_13_R2.IBlockState;

import java.util.*;

@PatchyAutoTranslation
public final class v13ConversionBridge implements ConversionBridge {
  @PatchyAutoTranslation
  public Map<Setting<?>, Comparable<?>> settingsOf(Object blockData) {
    net.minecraft.server.v1_13_R2.IBlockData data = (net.minecraft.server.v1_13_R2.IBlockData) blockData;
    Set<IBlockState<?>> states = data.getStateMap().keySet();
    if (states.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<Setting<?>, Comparable<?>> configuration = new HashMap<>();
    for (net.minecraft.server.v1_13_R2.IBlockState<?> state : states) {
      Setting<?> setting = SettingCache.computeSettingIfAbsent(state, this::convertSetting);
      configuration.put(setting, convertEnumToIndexIfPresent(data.get(state)));
    }
    return configuration;
  }

  @PatchyAutoTranslation
  public Setting<?> convertSetting(Object blockState) {
    net.minecraft.server.v1_13_R2.IBlockState<?> state = (net.minecraft.server.v1_13_R2.IBlockState<?>) blockState;
    String name = state.a();
    if (state instanceof BlockStateInteger) {
      BlockStateInteger blockStateInteger = (BlockStateInteger) state;
      Collection<Integer> values = blockStateInteger.d();
      IntSummaryStatistics statistics = values.stream().mapToInt(value -> value).summaryStatistics();
      return Settings.integerSetting(name, statistics.getMin(), statistics.getMax());
    } else if (state instanceof BlockStateBoolean) {
      return Settings.booleanSetting(name);
    } else if (state instanceof BlockStateEnum) {
      return Settings.enumSetting(name, state.b(), state.d());
    }
    throw new IllegalStateException("Unknown block state " + state + " (" + name + ")");
  }

  @PatchyAutoTranslation
  private static Comparable<?> convertEnumToIndexIfPresent(Comparable<?> initial) {
    if (initial.getClass().isEnum()) {
      return ((Enum<?>) initial).name();
    }
    return initial;
  }
}
