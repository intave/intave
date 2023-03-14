package de.jpx3.intave.trustfactor;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.access.player.trust.TrustFactor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class YamlTrustFactorConfiguration implements TrustFactorConfiguration {
  private final Map<String, EnumMap<TrustFactor, Integer>> settingsMap = new HashMap<>();

  public YamlTrustFactorConfiguration(YamlConfiguration configuration) {
    apply(configuration);
  }

  private void apply(YamlConfiguration trustFactorSettings) {
    for (Map.Entry<String, Object> configEntry : trustFactorSettings.getValues(true).entrySet()) {
      if (configEntry.getValue() instanceof ArrayList) {
        List<?> values = (List<?>) configEntry.getValue();
        //noinspection unchecked
        apply(configEntry.getKey(), (List<Integer>) values);
      }
    }
  }

  private void apply(String key, List<Integer> values) {
    TrustFactor[] trustFactors = TrustFactor.values();
    EnumMap<TrustFactor, Integer> enumMap =
      IntStream.range(0, trustFactors.length)
        .boxed()
        .collect(Collectors.toMap(j -> trustFactors[j], values::get, (a, b) -> b, () -> new EnumMap<>(TrustFactor.class)));
    settingsMap.put(key, enumMap);
  }

  @Override
  public int resolveSetting(String key, TrustFactor trustFactor) {
    EnumMap<TrustFactor, Integer> trustFactorIntegerEnumMap = settingsMap.get(key.toLowerCase(Locale.ROOT));
    if (trustFactorIntegerEnumMap == null) {
      return 0;
    }
    try {
      return trustFactorIntegerEnumMap.get(trustFactor);
    } catch (NullPointerException exception) {
      throw new IntaveInternalException("Unable to fetch trustfactor setting " + key + " for trustfactor " + trustFactor.name(), exception);
    }
  }
}
