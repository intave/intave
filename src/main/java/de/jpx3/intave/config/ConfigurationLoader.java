package de.jpx3.intave.config;

import com.google.common.hash.Hashing;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.security.ContextSecrets;
import de.jpx3.intave.security.SSLConnectionVerifier;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.tools.annotate.Nullable;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Locale;
import java.util.UUID;

import static de.jpx3.intave.IntaveControl.GOMME_MODE;

public final class ConfigurationLoader {
  private final static String CONF_CACHE_FILE_SUFFIX = "x";
  private final static String SECRET_KEY = "AES/GCM/NoPadding";

  private final String configurationKey;
  private YamlConfiguration configuration;

  public ConfigurationLoader(String configurationKey) {
    this.configurationKey = configurationKey;
  }

  @Native
  @Nullable
  public String precomputeConfigurationHash() {
    if(!configurationCacheExists()) {
      return null;
    }
    try {
      FileInputStream fileInputStream = new FileInputStream(configurationCache());
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int read;
      while((read = fileInputStream.read(buf)) != -1) {
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
  }

  @Native
  public void loadConfigurationUpdatedForcefully() {
    YamlConfiguration configuration = tryDownloadConfiguration();
    if(configuration == null) {
      try {
        configuration = readConfiguration();
      } catch (IllegalStateException exception) {
        throw new IllegalStateException("Unable to prepare configuration");
      }
    } else {
      saveConfiguration(configuration);
    }
    this.configuration = configuration;
  }

  @Native
  public void loadConfiguration() {
    YamlConfiguration configuration;
    if(!configurationCacheExists()) {
      configuration = tryDownloadConfiguration();
      if(configuration == null) {
        try {
          configuration = readConfiguration();
        } catch (IllegalStateException exception) {
          throw new IllegalStateException("Unable to prepare configuration");
        }
      } else {
        saveConfiguration(configuration);
      }
    } else {
      try {
        configuration = readConfiguration();
      } catch (IllegalStateException exception) {
        configuration = tryDownloadConfiguration();
        if(configuration == null) {
          throw exception;
        }
      }
    }
    this.configuration = configuration;
  }

  @Native
  private YamlConfiguration tryDownloadConfiguration() {
    try {
      InputStream inputStream;
      if(IntaveControl.USE_DEBUG_RESOURCES) {
        inputStream = ConfigurationLoader.class.getResourceAsStream("/config-internal.yml");
      } else {
        URL url = new URL("https://intave.de/api/configuration-download.php");
        URLConnection urlConnection = url.openConnection();
        urlConnection.addRequestProperty("User-Agent", "Intave/"+IntavePlugin.version());
        urlConnection.addRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
        urlConnection.setUseCaches(false);
        urlConnection.addRequestProperty("Pragma", "no-cache");
        urlConnection.addRequestProperty("Identifier", "ID");
        urlConnection.addRequestProperty("ConfigKey", configurationKey);
        urlConnection.setConnectTimeout(3000);
        urlConnection.setReadTimeout(3000);
        urlConnection.connect();
        SSLConnectionVerifier.verifyURLConnection((HttpsURLConnection) urlConnection);
        inputStream = urlConnection.getInputStream();
      }
      return YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream));
    } catch (IOException exception) {
      exception.printStackTrace();
      return null;
    }
  }

  @Native
  private YamlConfiguration readConfiguration() {
    try {
      File configurationCache = configurationCache();
      if(!configurationCache.exists()) {
        throw new IllegalStateException();
      }
      FileInputStream fileInputStream = new FileInputStream(configurationCache);
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int read;
      while((read = fileInputStream.read(buf)) != -1) {
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
      return YamlConfiguration.loadConfiguration(new InputStreamReader(new ByteArrayInputStream(output)));
    } catch (Exception exception) {
      throw new IllegalStateException(/*exception*/);
    }
  }

  @Native
  private void saveConfiguration(YamlConfiguration configuration) {
    try {
      File configurationCache = configurationCache();
      if (configurationCache.exists()) {
        configurationCache.delete();
      }
      configurationCache.createNewFile();
      String configurationContent = configuration.saveToString();
      SecureRandom secureRandom = new SecureRandom();
      byte[] iv = new byte[12];
      secureRandom.nextBytes(iv);
      SecretKey secretKey = generateSecretKey(iv);
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
    } catch (Exception  exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Native
  private SecretKey generateSecretKey(byte[] iv) throws NoSuchAlgorithmException, InvalidKeySpecException {
    KeySpec spec = new PBEKeySpec(ConfigurationLoader.SECRET_KEY.toCharArray(), iv, 65536, 128); // AES-128
    SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
    byte[] key = secretKeyFactory.generateSecret(spec).getEncoded();
    return new SecretKeySpec(key, "AES");
  }

  public void deleteCaches() {
    configurationCache().delete();
  }

  public boolean configurationCacheExists() {
    return configurationCache().exists();
  }

  private File configurationCache() {
    String fileName = new UUID(((long)configurationKey.length() << 8) | (configurationKey.hashCode() >>> 2),  ~configurationKey.hashCode()).toString();
    fileName = fileName/*.substring(0, fileName.length() - 1)*/ + CONF_CACHE_FILE_SUFFIX;
    return new File(intaveTempDirectory(), fileName);
  }

  private File intaveTempDirectory() {
    File workDirectory;
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    if(operatingSystem.contains("win")) {
      workDirectory = new File(System.getenv("APPDATA") + "/Intave");
    } else {
      if(GOMME_MODE) {
        workDirectory = new File(ContextSecrets.secret("cache-directory"));
      } else {
        workDirectory = new File("/home/.intave/");
      }
    }
    if(!workDirectory.exists()) {
      workDirectory.mkdir();
    }
    return workDirectory;
  }

  public YamlConfiguration configuration() {
    return configuration;
  }
}
