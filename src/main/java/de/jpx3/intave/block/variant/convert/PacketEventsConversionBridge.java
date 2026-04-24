package de.jpx3.intave.block.variant.convert;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateValue;
import de.jpx3.intave.block.variant.Setting;
import de.jpx3.intave.block.variant.Settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class PacketEventsConversionBridge implements ConversionBridge {
  @Override
  public Map<Setting<?>, Comparable<?>> settingsOf(Object blockData) {
    if (!(blockData instanceof WrappedBlockState)) {
      return Collections.emptyMap();
    }
    Map<StateValue, Object> data = ((WrappedBlockState) blockData).getInternalData();
    if (data.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<Setting<?>, Comparable<?>> configuration = new HashMap<>();
    for (Map.Entry<StateValue, Object> entry : data.entrySet()) {
      Object value = entry.getValue();
      if (!(value instanceof Comparable)) {
        continue;
      }
      configuration.put(settingOf(entry.getKey()), comparableValue(value));
    }
    return configuration;
  }

  private Setting<?> settingOf(StateValue stateValue) {
    return SettingCache.computeSettingIfAbsent(stateValue, ignored -> convert(stateValue));
  }

  private Setting<?> convert(StateValue stateValue) {
    Class<?> type = stateValue.getDataClass();
    if (type == Boolean.TYPE || type == Boolean.class) {
      return Settings.booleanSetting(stateValue.getName());
    }
    if (type == Integer.TYPE || type == Integer.class) {
      return Settings.integerSetting(stateValue.getName(), 0, 64);
    }
    if (type.isEnum()) {
      return Settings.enumSetting(stateValue.getName(), type, Arrays.asList(type.getEnumConstants()));
    }
    throw new IllegalStateException("Unknown PacketEvents block state " + stateValue.getName() + " (" + type.getName() + ")");
  }

  private Comparable<?> comparableValue(Object value) {
    if (value.getClass().isEnum()) {
      return ((Enum<?>) value).name();
    }
    return (Comparable<?>) value;
  }
}
