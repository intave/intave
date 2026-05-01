package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ConfigurationRecovery {

  private static final String BUGGED_CONFIG_FOLDER = "bugged config";

  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");

  private static final Set<String> VERSION_WARNINGS = ConcurrentHashMap.newKeySet();

  private static final Map<String, ConfigurationType> CONFIGURATION_TYPES = Map.of(
      "config.yml", new ConfigurationType("config.yml", "prefix", "14.0.0"),
      "advanced.yml", new ConfigurationType("advanced.yml", "layout.prefix", "14.0.0")
  );

  private ConfigurationRecovery() {}

  static YamlConfiguration loadConfiguration(File file, String defaultResource) {
    ensureConfigurationExists(file, defaultResource);

    try {
      YamlConfiguration configuration = loadFromFile(file);

      VersionState state = validate(configuration, defaultResource);
      if (state != VersionState.OK) {
        handleVersionState(state, file, defaultResource);
      }

      return configuration;

    } catch (Exception exception) {
      return recoverConfiguration(file, defaultResource, exception);
    }
  }

  static void ensureConfigurationExists(File file, String defaultResource) {
    Resource resource = Resources.resourceFromFile(file);
    if (resource.available()) return;

    writeDefaultSafe(file, defaultResource);
  }

  static YamlConfiguration recoverConfiguration(File file, String defaultResource, Exception exception) {
    byte[] defaultBytes = recover(file, defaultResource, exception);

    try {
      YamlConfiguration configuration = loadFromBytes(defaultBytes);

      VersionState state = validate(configuration, defaultResource);
      if (state != VersionState.OK) {
        handleVersionState(state, file, defaultResource);
      }

      return configuration;

    } catch (Exception recoveredException) {
      IntavePlugin.singletonInstance()
          .logger()
          .error("CRITICAL: Failed to recover " + file.getName() + ", using empty fallback", recoveredException);

      return new YamlConfiguration();
    }
  }

  private static byte[] recover(File file, String defaultResource, Exception exception) {
    IntavePlugin.singletonInstance()
        .logger()
        .error("Invalid " + file.getName() + ", moving to backup", exception);

    moveBuggedConfiguration(file);
    return writeDefaultSafe(file, defaultResource);
  }

  private static void handleVersionState(VersionState state, File file, String resource) {
    if (state == VersionState.MISMATCH) {
      IntavePlugin.singletonInstance()
          .logger()
          .warn("Config outdated or incompatible → regenerating: " + file.getName());

      moveBuggedConfiguration(file);
      writeDefaultSafe(file, resource);
    }
  }

  private static YamlConfiguration loadFromFile(File file)
      throws IOException, InvalidConfigurationException {

    YamlConfiguration configuration = new YamlConfiguration();
    configuration.load(file);
    return configuration;
  }

  private static VersionState validate(YamlConfiguration configuration, String resourceName) {

    ConfigurationType type =
        CONFIGURATION_TYPES.get(resourceName.toLowerCase(Locale.ROOT));

    if (type == null) {
      throw new IllegalStateException(
          "No configuration schema registered for: " + resourceName
      );
    }

    validatePrefix(configuration, type.prefixPath);
    return validateVersion(configuration, type);
  }

  private static void validatePrefix(YamlConfiguration configuration, String path) {
    if (!configuration.isString(path)) {
      throw new IllegalArgumentException("Missing or invalid: " + path);
    }

    String value = configuration.getString(path);
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("Empty value: " + path);
    }
  }

  private static VersionState validateVersion(
      YamlConfiguration configuration,
      ConfigurationType type
  ) {

    String configured = configuration.getString("version");

    if (configured == null || configured.trim().isEmpty()) {
      warnOnce(type.resourceName,
          "Missing version in " + type.resourceName +
          " (skipping migration, keeping file)");
      return VersionState.MISSING;
    }

    int cmp = compareSchemaVersions(configured, type.schemaVersion);

    if (cmp != 0) {
      String direction = cmp < 0 ? "upgrade required" : "downgrade detected";

      warnOnce(type.resourceName,
          "Schema mismatch " + type.resourceName +
          ": found " + configured +
          ", expected " + type.schemaVersion +
          " (" + direction + ")");

      return VersionState.MISMATCH;
    }

    return VersionState.OK;
  }

  private static void warnOnce(String key, String message) {
    if (VERSION_WARNINGS.add(key)) {
      IntavePlugin.singletonInstance().logger().warn(message);
    }
  }

  private static void moveBuggedConfiguration(File file) {
    if (!file.exists()) return;

    File folder = new File(file.getParentFile(), BUGGED_CONFIG_FOLDER);

    if (!folder.exists()) {
      try {
        if (!folder.mkdirs() && !folder.exists()) {
          IntavePlugin.singletonInstance()
              .logger()
              .warn("Cannot create backup folder: " + folder.getAbsolutePath());
          return;
        }
      } catch (Exception e) {
        IntavePlugin.singletonInstance()
            .logger()
            .warn("Failed to create backup folder: " + e.getMessage());
        return;
      }
    }

    String timestamp = DATE_FORMAT.format(LocalDateTime.now());
    File target = new File(folder, timestamp + "-" + file.getName());

    int i = 1;
    while (target.exists()) {
      target = new File(folder, timestamp + "-" + i++ + "-" + file.getName());
    }

    try {
      Files.move(file.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e1) {
      try {
        Files.move(file.toPath(), target.toPath());
      } catch (IOException e2) {
        IntavePlugin.singletonInstance()
            .logger()
            .warn("Failed to backup corrupted config: " + e2.getMessage());
      }
    }
  }

  private static byte[] writeDefaultSafe(File file, String defaultResource) {
    try {
      return writeDefault(file, defaultResource);
    } catch (Exception e) {
      IntavePlugin.singletonInstance()
          .logger()
          .error("Failed to write default config: " + defaultResource, e);
      return new byte[0];
    }
  }

  private static byte[] writeDefault(File file, String defaultResource) throws IOException {
    Resource jar = Resources.resourceFromJarOrBuild(defaultResource);
    Resource out = Resources.resourceFromFile(file);

    byte[] data = readFully(jar, defaultResource);
    out.write(data);
    return data;
  }

  private static byte[] readFully(Resource resource, String name) throws IOException {
    try (InputStream in = resource.read()) {

      if (in == null) {
        throw new IOException("Missing resource: " + name);
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];

      int r;
      while ((r = in.read(buffer)) != -1) {
        out.write(buffer, 0, r);
      }

      return out.toByteArray();
    }
  }

  private static YamlConfiguration loadFromBytes(byte[] bytes)
      throws InvalidConfigurationException {

    YamlConfiguration cfg = new YamlConfiguration();
    cfg.loadFromString(new String(bytes, StandardCharsets.UTF_8));
    return cfg;
  }

  private static int compareSchemaVersions(String a, String b) {
    int[] av = parse(a);
    int[] bv = parse(b);

    int len = Math.max(av.length, bv.length);

    for (int i = 0; i < len; i++) {
      int x = i < av.length ? av[i] : 0;
      int y = i < bv.length ? bv[i] : 0;

      int cmp = Integer.compare(x, y);
      if (cmp != 0) return cmp;
    }

    return 0;
  }

  private static int[] parse(String v) {
    String[] parts = v.split("\\.");
    int[] out = new int[parts.length];

    for (int i = 0; i < parts.length; i++) {
      out[i] = parsePart(parts[i]);
    }

    return out;
  }

  private static int parsePart(String part) {
    int i = 0;

    while (i < part.length() && Character.isDigit(part.charAt(i))) {
      i++;
    }

    if (i == 0) return 0;

    try {
      return Integer.parseInt(part.substring(0, i));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static final class ConfigurationType {
    final String resourceName;
    final String prefixPath;
    final String schemaVersion;

    ConfigurationType(String r, String p, String s) {
      this.resourceName = r;
      this.prefixPath = p;
      this.schemaVersion = s;
    }
  }

  private enum VersionState {
    OK,
    MISSING,
    MISMATCH
  }
}