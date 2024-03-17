package de.jpx3.intave.check;

import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.access.IntaveBootFailureException;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.access.check.MitigationStrategy;
import de.jpx3.intave.math.MathHelper;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;

import java.util.*;

public final class CheckConfiguration {
  private final Check check;
  private volatile CheckSettings settingsAccess;

  public CheckConfiguration(Check check) {
    this.check = check;
  }

  public Check check() {
    return check;
  }

  public CheckSettings settings() {
    return settingsAccess;
  }

  public void setSettings(Map<String, Object> settings) {
    this.settingsAccess = new CheckSettings(settings, this);
  }

  public static class CheckSettings {
    private final Map<String, Object> access;
    private final CheckConfiguration configurationCache;
    private final Map<String, Map<Integer, List<String>>> thresholds = new HashMap<>();

    public CheckSettings(
      Map<String, Object> access,
      CheckConfiguration configurationCache
    ) {
      this.access = ImmutableMap.copyOf(access);
      this.configurationCache = configurationCache;
    }

    public Map<Integer, List<String>> defaultThresholds() {
      return thresholdsBy("thresholds");
    }

    public Map<Integer, List<String>> thresholdsBy(String key) {
      if (thresholds.containsKey(key)) {
        return thresholds.get(key);
      }
      ConfigurationSection configurationSection = (ConfigurationSection) access.get(key);
      Map<Integer, List<String>> thresholdMap = new LinkedHashMap<>();
      if (configurationSection == null) {
        return new HashMap<>();
      }
      Set<String> section = configurationSection.getKeys(false);
      if (section == null) {
        throw new IntaveInternalException("Unable to locate threshold section " + key + " in check " + configurationCache.check().name());
      }
      for (String configurationSectionKey : section) {
        List<String> output = new ArrayList<>();
        if (configurationSection.isList(configurationSectionKey)) {
          output.addAll(configurationSection.getStringList(configurationSectionKey));
        } else {
          output.add(configurationSection.getString(configurationSectionKey));
        }
        thresholdMap.put(Integer.parseInt(configurationSectionKey), output);
      }
      thresholds.put(key, thresholdMap);
      return thresholdMap;
    }

    public boolean checkEnabled() {
      return boolBy("enabled");
    }

    public MitigationStrategy mitigationStrategy() {
      return MitigationStrategy.byName(stringBy("mitigation"));
    }

    public List<String> stringListBy(String key) {
      //noinspection unchecked
      return (List<String>) uncheckedResolveOrDefault(key, new ArrayList<String>());
    }

    public String stringBy(String key) {
      return stringBy(key, "");
    }

    public String stringBy(String key, String def) {
      return (String) uncheckedResolveOrDefault(key, def);
    }

    public boolean boolBy(String key) {
      return boolBy(key, false);
    }

    public boolean boolBy(String key, boolean def) {
      try {
        Object val = uncheckedResolveOrDefault(key, def);
        if (val instanceof String) {
          return Boolean.parseBoolean((String) val);
        }
        if (val instanceof Number) {
          return ((Number) val).intValue() != 0;
        }
        return (boolean) val;
      } catch (ClassCastException exception) {
        throw new IntaveBootFailureException(new InvalidConfigurationException("Expected " + key + " in check " + configurationCache.check().name() + " to be a boolean expression", exception));
      }
    }

    public double doubleBy(String key) {
      return doubleBy(key, 0);
    }

    public double doubleInBoundsBy(String key, double min, double max) {
      return doubleInBoundsBy(key, min, max, 0);
    }

    public double doubleInBoundsBy(String key, double min, double max, double def) {
      return MathHelper.minmax(min, doubleBy(key, def), max);
    }

    public double doubleBy(String key, double def) {
      try {
        return (double) uncheckedResolveOrDefault(key, def);
      } catch (ClassCastException exception) {
        return Double.parseDouble(String.valueOf(intBy(key, (int) def)));
      }
    }

    public long longBy(String key) {
      return longBy(key, 0);
    }

    public long longInBoundsBy(String key, long min, long max) {
      return longInBoundsBy(key, min, max, 0);
    }

    public long longInBoundsBy(String key, long min, long max, long def) {
      return MathHelper.minmax(min, longBy(key, def), max);
    }

    public long longBy(String key, long def) {
      try {
        return (long) uncheckedResolveOrDefault(key, def);
      } catch (ClassCastException exception) {
        return Long.parseLong(String.valueOf(intBy(key, (int) def)));
      }
    }

    public int intBy(String key) {
      return intBy(key, 0);
    }

    public int intInBoundsBy(String key, int min, int max) {
      return intInBoundsBy(key, min, max, 0);
    }

    public int intInBoundsBy(String key, int min, int max, int def) {
      return MathHelper.minmax(min, intBy(key, def), max);
    }

    public int intBy(String key, int def) {
      try {
        return (int) uncheckedResolveOrDefault(key, def);
      } catch (ClassCastException exception) {
        throw new IntaveBootFailureException(new InvalidConfigurationException("Expected " + key + " in check " + configurationCache.check().name() + " to be a numeric expression", exception));
      }
    }

    private Object uncheckedResolveOrDefault(String key, Object def) {
      return access.getOrDefault(key, def);
    }

    public boolean has(String key) {
      return access.containsKey(key);
    }
  }
}
