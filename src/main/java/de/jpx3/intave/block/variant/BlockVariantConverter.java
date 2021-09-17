package de.jpx3.intave.block.variant;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.clazz.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.clazz.rewrite.PatchyLoadingInjector;
import net.minecraft.server.v1_14_R1.*;
import org.bukkit.Material;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockVariantConverter {
  static {
    ClassLoader classLoader = BlockVariantConverter.class.getClassLoader();
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.block.variant.BlockVariantConverter$Bridge");
  }

  public static Map<Integer, BlockVariant> translate(Material type, Map<Integer, Object> natives) {
    Map<Integer, BlockVariant> map = new HashMap<>();
    natives.forEach((integer, nativeBlock) -> map.put(integer, translate(type, nativeBlock)));
    return map;
  }

  public final static BlockVariant EMPTY = new EmptyBlockVariant();

  private static BlockVariant translate(Material type, Object blockData) {
    Map<Setting<?>, Comparable<?>> settings = Bridge.settingsOf(blockData);
    if (settings.isEmpty()) {
      return EMPTY;
    }
    return new IndexedBlockVariant(type, settings);
  }

  @PatchyAutoTranslation
  public static class Bridge {
    private static final Map<Object, Setting<?>> settingCache = new ConcurrentHashMap<>();
    private final static boolean AQUATIC_RESOLVE = MinecraftVersions.VER1_14_0.atOrAbove();
    private final static boolean MODERN_RESOLVE = MinecraftVersions.VER1_16_0.atOrAbove();

    private static Map<Setting<?>, Comparable<?>> settingsOf(Object blockData) {
      if (MODERN_RESOLVE) {
        return modernSettingsOf(blockData);
      } else if (AQUATIC_RESOLVE) {
        return aquaticSettingsOf(blockData);
      } else {
        return legacySettingsOf(blockData);
      }
    }

    @PatchyAutoTranslation
    private static Map<Setting<?>, Comparable<?>> modernSettingsOf(Object blockData) {
      net.minecraft.server.v1_16_R1.IBlockData data = (net.minecraft.server.v1_16_R1.IBlockData) blockData;
      Set<net.minecraft.server.v1_16_R1.IBlockState<?>> states = data.getStateMap().keySet();
      Map<Setting<?>, Comparable<?>> configuration = new HashMap<>();
      for (net.minecraft.server.v1_16_R1.IBlockState<?> state : states) {
        Setting<?> setting = settingCache.get(state);
        if (setting == null) {
          setting = modernConvertSetting(state);
          settingCache.put(state, setting);
        }
        configuration.put(setting, convertData(data.get(state)));
      }
      return configuration;
    }

    @PatchyAutoTranslation
    private static Setting<?> modernConvertSetting(Object blockState) {
      net.minecraft.server.v1_16_R1.IBlockState<?> state = (net.minecraft.server.v1_16_R1.IBlockState<?>) blockState;
      String name = state.getName();
      if (state instanceof net.minecraft.server.v1_16_R1.BlockStateInteger) {
        net.minecraft.server.v1_16_R1.BlockStateInteger blockStateInteger = (net.minecraft.server.v1_16_R1.BlockStateInteger) state;
        Collection<Integer> values = blockStateInteger.getValues();
        Integer min = values.stream().min(Integer::compare).orElse(0);
        Integer max = values.stream().max(Integer::compare).orElse(0);
        return new IntegerSetting(name, min, max);
      } else if (state instanceof net.minecraft.server.v1_16_R1.BlockStateBoolean) {
        return new BooleanSetting(name);
      } else if (state instanceof net.minecraft.server.v1_16_R1.BlockStateEnum) {
        return new UnknownEnumSetting(name, state.getType(), state.getValues());
      }
      throw new IllegalStateException("Unknown block state " + state + " (" + name +")");
    }

    @PatchyAutoTranslation
    private static Map<Setting<?>, Comparable<?>> aquaticSettingsOf(Object blockData) {
      IBlockData data = (IBlockData) blockData;
      Set<IBlockState<?>> states = data.getStateMap().keySet();
      Map<Setting<?>, Comparable<?>> configuration = new HashMap<>();
      for (IBlockState<?> state : states) {
        Setting<?> setting = settingCache.get(state);
        if (setting == null) {
          setting = aquaticConvertSetting(state);
          settingCache.put(state, setting);
        }
        configuration.put(setting, convertData(data.get(state)));
      }
      return configuration;
    }

    @PatchyAutoTranslation
    private static Setting<?> aquaticConvertSetting(Object blockState) {
      IBlockState<?> state = (IBlockState<?>) blockState;
      String name = state.a();
      if (state instanceof BlockStateInteger) {
        BlockStateInteger blockStateInteger = (BlockStateInteger) state;
        Collection<Integer> values = blockStateInteger.getValues();
        Integer min = values.stream().min(Integer::compare).orElse(0);
        Integer max = values.stream().max(Integer::compare).orElse(0);
        return new IntegerSetting(name, min, max);
      } else if (state instanceof BlockStateBoolean) {
        return new BooleanSetting(name);
      } else if (state instanceof BlockStateEnum) {
        return new UnknownEnumSetting(name, state.b(), state.getValues());
      }
      throw new IllegalStateException("Unknown block state " + state + " (" + name +")");
    }

    @PatchyAutoTranslation
    private static Map<Setting<?>, Comparable<?>> legacySettingsOf(Object blockData) {
      net.minecraft.server.v1_13_R2.IBlockData data = (net.minecraft.server.v1_13_R2.IBlockData) blockData;
      Set<net.minecraft.server.v1_13_R2.IBlockState<?>> states = data.getStateMap().keySet();
      Map<Setting<?>, Comparable<?>> configuration = new HashMap<>();
      for (net.minecraft.server.v1_13_R2.IBlockState<?> state : states) {
        Setting<?> setting = settingCache.get(state);
        if (setting == null) {
          setting = legacyConvertSetting(state);
          settingCache.put(state, setting);
        }
        configuration.put(setting, convertData(data.get(state)));
      }
      return configuration;
    }

    @PatchyAutoTranslation
    private static Setting<?> legacyConvertSetting(Object blockState) {
      net.minecraft.server.v1_8_R3.IBlockState<?> state = (net.minecraft.server.v1_8_R3.IBlockState<?>) blockState;
      String name = state.a();
      if (state instanceof BlockStateInteger) {
        BlockStateInteger blockStateInteger = (BlockStateInteger) state;
        Collection<Integer> values = blockStateInteger.getValues();
        Integer min = values.stream().min(Integer::compare).orElse(0);
        Integer max = values.stream().max(Integer::compare).orElse(0);
        return new IntegerSetting(name, min, max);
      } else if (state instanceof BlockStateBoolean) {
        return new BooleanSetting(name);
      } else if (state instanceof BlockStateEnum) {
        return new UnknownEnumSetting(name, state.b(), state.c());
      }
      throw new IllegalStateException("Unknown block state " + state + " (" + name +")");
    }

    @PatchyAutoTranslation
    private static Comparable<?> convertData(Comparable<?> initial) {
      if (initial.getClass().isEnum()) {
        return ((Enum<?>) initial).ordinal();
      }
      return initial;
    }
  }
}
