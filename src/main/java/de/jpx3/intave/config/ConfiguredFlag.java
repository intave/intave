package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * A boolean option, read from wherever the owner actually wrote it.
 * <p>
 * The effective configuration is not always the {@code advanced.yml} on disk: with
 * {@code config: THIS} (the shipped default) Intave builds its settings from the
 * {@code advanced.yml} <i>inside the jar</i> plus the values converted out of
 * {@code config.yml}, and the owner's own {@code advanced.yml} is never read. An option
 * written into that file would then silently do nothing, which is a confusing way to lose
 * a setting — so this looks in the effective configuration first and then falls back to
 * the two files in the data folder. Whichever file it was written in, it counts.
 * <p>
 * Resolved once and cached, since the callers sit on hot paths. {@link #describe()}
 * reports the result and where it came from, so "the option is off" can be told apart
 * from "the option never reached Intave" without reading the source.
 */
public final class ConfiguredFlag {
  private static final String[] FALLBACK_FILES = {"advanced.yml", "config.yml"};

  private final String path;
  private final boolean fallback;

  private volatile Boolean valueCache;
  private volatile String sourceCache;

  public ConfiguredFlag(String path, boolean fallback) {
    this.path = path;
    this.fallback = fallback;
  }

  public boolean enabled() {
    Boolean cached = valueCache;
    if (cached != null) {
      return cached;
    }
    IntavePlugin plugin = IntavePlugin.singletonInstance();
    if (plugin == null) {
      // asked before the plugin is up: answer, but do not cache the answer
      return fallback;
    }
    YamlConfiguration settings = plugin.settings();
    if (settings != null && settings.isSet(path)) {
      return resolvedTo(settings.getBoolean(path), "effective configuration");
    }
    if (settings == null) {
      // asked before the configuration is up: answer, but do not cache the answer
      return fallback;
    }
    for (String fileName : FALLBACK_FILES) {
      File file = new File(plugin.dataFolder(), fileName);
      if (!file.isFile()) {
        continue;
      }
      YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(file);
      if (onDisk.isSet(path)) {
        return resolvedTo(onDisk.getBoolean(path), fileName);
      }
    }
    return resolvedTo(fallback, "default");
  }

  private boolean resolvedTo(boolean enabled, String source) {
    sourceCache = source;
    valueCache = enabled;
    return enabled;
  }

  public String describe() {
    boolean enabled = enabled();
    String source = sourceCache;
    return (enabled ? "on" : "off") + " (from " + (source == null ? "unresolved" : source) + ")";
  }
}
