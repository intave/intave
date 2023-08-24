package de.jpx3.intave.config;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveBootFailureException;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.connect.IntaveDomains;
import de.jpx3.intave.resource.legacy.EncryptedLegacyResource;
import de.jpx3.intave.security.ContextSecrets;
import de.jpx3.intave.security.LicenseAccess;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collector;

import static de.jpx3.intave.IntaveControl.GOMME_MODE;

public final class ConfigurationLoader {
  private static final String CONF_CACHE_FILE_SUFFIX = "x";
  private static final String SECRET_KEY = "AES/GCM/NoPadding";

  private final String configurationKey;
  private YamlConfiguration configuration;

  private final EncryptedLegacyResource configurationStates = new EncryptedLegacyResource("configuration-states", true);

  public ConfigurationLoader(String configurationKey) {
    this.configurationKey = configurationKey;
  }

/*  @Native
  @Nullable
  public String precomputeConfigurationHash() {
    if (!configurationCacheExists()) {
      return null;
    }
    try {
      FileInputStream fileInputStream = new FileInputStream(configurationCache());
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int read;
      while ((read = fileInputStream.read(buf)) != -1) {
        byteArrayOutputStream.write(buf, 0, read);
      }
      fileInputStream.close();
      byteArrayOutputStream.close();
      ByteBuffer byteBuffer = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
      byte[] iv = new byte[byteBuffer.getInt()];
      byteBuffer.get(iv);
      SecretKey secretKey = generateSecretKey(iv);
      byte[] cipherBytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(cipherBytes);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
      byte[] output = cipher.doFinal(cipherBytes);
      //noinspection UnstableApiUsage
      return Hashing.sha256().hashBytes(output).toString();
    } catch (Exception exception) {
      return null;
    }
  }*/

  private static final Collector<String, Map<String, Integer>, Map<String, Integer>> STATE_COLLECTOR = Collector.of(HashMap::new, (map, line) -> {
    if (line.contains(":")) {
      String[] split = line.split(":");
      map.put(split[0], Integer.parseInt(split[1]));
    }
  }, (map1, map2) -> {
    map1.putAll(map2);
    return map1;
  });

  @Native
  public int latestState() {
    if (!configurationStates.exists()) {
      return 0;
    }
    Map<String, Integer> mappings = configurationStates.collectLines(STATE_COLLECTOR);
    if (!mappings.containsKey(configurationKey)) {
      return -1;
    }
    return mappings.get(configurationKey.toLowerCase());
  }

  @Native
  public void saveState(int state) {
    Map<String, Integer> mappings = new HashMap<>();
    if (configurationStates.exists()) {
      mappings = configurationStates.collectLines(STATE_COLLECTOR);
    }
    mappings.put(configurationKey.toLowerCase(), state);
    StringBuilder content = new StringBuilder();
    mappings.forEach((key, value) -> content.append(key).append(":").append(value).append("\r\n"));
    configurationStates.write(new ByteArrayInputStream(content.toString().getBytes(StandardCharsets.UTF_8)));
  }

  @Native
  public void loadConfigurationUpdatedForcefully() {
//    IntaveLogger.logger().info("Downloading configuration..");
    YamlConfiguration configuration = tryDownloadConfiguration();
    if (configuration == null) {
//      IntaveLogger.logger().info("Download failed");
      try {
        configuration = (YamlConfiguration) readConfiguration();
      } catch (IllegalStateException exception) {
        throw new IllegalStateException("Unable to prepare configuration");
      }
    } else {
//      IntaveLogger.logger().info("Saving configuration..");
      saveConfiguration(configuration);
    }
    this.configuration = configuration;
  }

  public void loadConfiguration() {
    YamlConfiguration configuration;
    if (!configurationCacheExists()) {
//      IntaveLogger.logger().info("No configuration found in cache? Downloading instead..");
      configuration = tryDownloadConfiguration();
      if (configuration == null) {
        try {
          configuration = (YamlConfiguration) readConfiguration();
        } catch (IllegalStateException exception) {
          throw new IllegalStateException("Unable to prepare configuration");
        }
      } else {
//        IntaveLogger.logger().info("Saving downloaded config to cache..");
        saveConfiguration(configuration);
      }
    } else {
      try {
        configuration = (YamlConfiguration) readConfiguration();
      } catch (IllegalStateException exception) {
        configuration = tryDownloadConfiguration();
        if (configuration == null) {
          throw exception;
        }
        saveConfiguration(configuration);
      }
    }
    this.configuration = configuration;
  }

  private YamlConfiguration tryDownloadConfiguration() {
    try {
      InputStream inputStream;
      boolean useExternalConfigurationFile = ("file".equalsIgnoreCase(configurationKey)/* && enterprise*/) || IntaveControl.USE_EXTERNAL_CONFIGURATION_FILE;
      if (useExternalConfigurationFile) {
        IntavePlugin plugin = IntavePlugin.singletonInstance();
        File settingFile = new File(plugin.dataFolder(), "settings.yml");
        if (!settingFile.exists()) {
          if (plugin.getResource("settings.yml") != null) {
            saveResource("settings.yml", false);
          } else {
            throw new IntaveBootFailureException("Please download Intave again to use file configurations");
          }
        }
        inputStream = Files.newInputStream(settingFile.toPath());
      } else if (IntaveControl.USE_DEBUG_TRUSTFACTOR_RESOURCE) {
        inputStream = ConfigurationLoader.class.getResourceAsStream("/settings.yml");
        if (inputStream == null) {
          throw new IntaveBootFailureException("Debug resources not found");
        }
      } else {
        URL url = new URL("https://"+ IntaveDomains.primaryServiceDomain()+"/settings/download");
        URLConnection urlConnection = url.openConnection();
        urlConnection.addRequestProperty("User-Agent", "Intave/" + IntavePlugin.version());
        urlConnection.addRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
        urlConnection.setUseCaches(false);
        urlConnection.addRequestProperty("Pragma", "no-cache");
        urlConnection.addRequestProperty("Identifier", LicenseAccess.rawLicense());
        urlConnection.addRequestProperty("ConfigKey", configurationKey);
        urlConnection.setConnectTimeout(3000);
        urlConnection.setReadTimeout(3000);
        urlConnection.connect();
        inputStream = urlConnection.getInputStream();
      }
      return YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream));
    } catch (Exception exception) {
//      exception.printStackTrace();
      return null;
    }
  }

  // stolen from bukkit
  public void saveResource(String resourcePath, boolean replace) {
    if (resourcePath != null && !resourcePath.equals("")) {
      resourcePath = resourcePath.replace('\\', '/');
      InputStream in = this.getResource(resourcePath);
      if (in == null) {
        throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found");
      } else {
        File dataFolder = IntavePlugin.singletonInstance().dataFolder();
        File outFile = new File(dataFolder, resourcePath);
        int lastIndex = resourcePath.lastIndexOf(47);
        File outDir = new File(dataFolder, resourcePath.substring(0, Math.max(lastIndex, 0)));
        if (!outDir.exists()) {
          outDir.mkdirs();
        }
        try {
          if (!outFile.exists() || replace) {
            OutputStream out = Files.newOutputStream(outFile.toPath());
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) != -1) {
              out.write(buf, 0, len);
            }
            out.close();
            in.close();
          }
        } catch (IOException ignored) {
        }
      }
    } else {
      throw new IllegalArgumentException("ResourcePath cannot be null or empty");
    }
  }

  public InputStream getResource(String filename) {
    if (filename == null) {
      throw new IllegalArgumentException("Filename cannot be null");
    } else {
      try {
        URL url = this.getClass().getClassLoader().getResource(filename);
        if (url == null) {
          return null;
        } else {
          URLConnection connection = url.openConnection();
          connection.setUseCaches(false);
          return connection.getInputStream();
        }
      } catch (IOException var4) {
        return null;
      }
    }
  }

  @Native
  private Object readConfiguration() {
    try {
      File configurationCache = configurationCache();
      if (!configurationCache.exists()) {
        throw new IllegalStateException();
      }
      configurationCache.setLastModified(System.currentTimeMillis());
      FileInputStream fileInputStream = new FileInputStream(configurationCache);
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int read;
      while ((read = fileInputStream.read(buf)) != -1) {
        byteArrayOutputStream.write(buf, 0, read);
      }
      fileInputStream.close();
      byteArrayOutputStream.close();
      ByteBuffer byteBuffer = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
      byte[] iv = new byte[byteBuffer.getInt()];
      byteBuffer.get(iv);
      KeySpec spec = new PBEKeySpec(ConfigurationLoader.SECRET_KEY.toCharArray(), iv, 65536, 128); // AES-128
      SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      byte[] key = secretKeyFactory.generateSecret(spec).getEncoded();
      SecretKey secretKey = new SecretKeySpec(key, "AES");
      byte[] cipherBytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(cipherBytes);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
      byte[] output = cipher.doFinal(cipherBytes);
      InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(output));
      return YamlConfiguration.loadConfiguration(reader);
    } catch (Exception exception) {
      throw new IllegalStateException(/*exception*/);
    }
  }

  @Native
  private void saveConfiguration(Object configurationObj) {
    try {
      YamlConfiguration configuration = (YamlConfiguration) configurationObj;
      int state = configuration.getInt("variant");
      if (state != latestState()) {
        saveState(state);
      }
      File configurationCache = configurationCache();
      if (configurationCache.exists()) {
        configurationCache.delete();
      }
      configurationCache.createNewFile();
      String configurationContent = configuration.saveToString();
      SecureRandom secureRandom = new SecureRandom();
      byte[] iv = new byte[12];
      secureRandom.nextBytes(iv);
      KeySpec spec = new PBEKeySpec(ConfigurationLoader.SECRET_KEY.toCharArray(), iv, 65536, 128); // AES-128
      SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      byte[] key = secretKeyFactory.generateSecret(spec).getEncoded();
      SecretKey secretKey = new SecretKeySpec(key, "AES");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
      byte[] encryptedData = cipher.doFinal(configurationContent.getBytes(StandardCharsets.UTF_8));
      ByteBuffer byteBuffer = ByteBuffer.allocate(4 + iv.length + encryptedData.length);
      byteBuffer.putInt(iv.length);
      byteBuffer.put(iv);
      byteBuffer.put(encryptedData);
      FileOutputStream fileOutputStream = new FileOutputStream(configurationCache);
      fileOutputStream.write(byteBuffer.array());
      fileOutputStream.close();
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  public void deleteCaches() {
    configurationCache().delete();
  }

  public boolean configurationCacheExists() {
    return configurationCache().exists();
  }

  public File configurationCache() {
    String fileName = new UUID(((long) configurationKey.length() << 8) | (configurationKey.hashCode() >>> 1), ~configurationKey.hashCode()).toString();
    fileName = fileName/*.substring(0, fileName.length() - 1)*/ + CONF_CACHE_FILE_SUFFIX;
    return new File(intaveTempDirectory(), fileName);
  }

  private File intaveTempDirectory() {
    File workDirectory;
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    if (operatingSystem.contains("win")) {
      workDirectory = new File(System.getenv("APPDATA") + "/Intave");
    } else {
      if (GOMME_MODE) {
        workDirectory = new File(ContextSecrets.secret("cache-directory"));
      } else {
        workDirectory = new File(System.getProperty("user.home") + "/.intave/");
      }
    }
    if (!workDirectory.exists()) {
      workDirectory.mkdir();
    }
    return workDirectory;
  }

  public YamlConfiguration configuration() {
    return configuration;
  }
}
