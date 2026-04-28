package de.jpx3.intave.block.variant.convert;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.variant.Setting;
import de.jpx3.intave.block.variant.Settings;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_16_R1.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

@PatchyAutoTranslation
final class v16ConversionBridge implements ConversionBridge {
  private static Method stateMapField;

  @PatchyAutoTranslation
  public Map<Setting<?>, Comparable<?>> settingsOf(Object blockData) {
    IBlockData data = (IBlockData) blockData;
    Set<IBlockState<?>> states;
    if (MinecraftVersions.VER26_1_1.atOrAbove()) {
      try {
        if (stateMapField == null) {
          stateMapField = IBlockData.class.getMethod("getProperties");
        }
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
	    try {
		    //noinspection unchecked
		    Collection<IBlockState<?>> properties = (Collection<IBlockState<?>>) stateMapField.invoke(data);
        states = new HashSet<>(properties);
      } catch (IllegalAccessException | InvocationTargetException e) {
		    throw new RuntimeException(e);
	    }
    } else if (MinecraftVersions.VER1_21.atOrAbove()) {
      try {
        if (stateMapField == null) {
          stateMapField = IBlockData.class.getMethod("getValues");
        }
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      try {
        Map<?, ?> map = (Map<?, ?>) stateMapField.invoke(data);
        //noinspection unchecked
        states = (Set<IBlockState<?>>) map.keySet();
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    } else {
      states = data.getStateMap().keySet();
    }
    if (states.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<Setting<?>, Comparable<?>> configuration = new HashMap<>();
    for (IBlockState<?> state : states) {
      configuration.put(
        SettingCache.computeSettingIfAbsent(state, this::convert),
        convertEnumToIndexIfPresent(data.get(state))
      );
    }
    return configuration;
  }

  @PatchyAutoTranslation
  private Setting<?> convert(Object blockState) {
    IBlockState<?> state = (IBlockState<?>) blockState;
    String name = state.getName();
    if (state instanceof BlockStateInteger) {
      BlockStateInteger blockStateInteger = (BlockStateInteger) state;
      Collection<Integer> values = blockStateInteger.getValues();
      IntSummaryStatistics statistics = values.stream().mapToInt(Integer::intValue).summaryStatistics();
      return Settings.integerSetting(name, statistics.getMin(), statistics.getMax());
    } else if (state instanceof BlockStateBoolean) {
      return Settings.booleanSetting(name);
    } else if (state instanceof BlockStateEnum) {
      return Settings.enumSetting(name, state.getType(), state.getValues());
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
