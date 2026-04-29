package de.jpx3.intave.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.jpx3.intave.IntavePlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutoUpdate {
  private static final String CONFIG_ROOT = "auto-update";
  private static final String REPOSITORY = "intave/intave";
  private static final String RELEASES_API = "https://api.github.com/repos/" + REPOSITORY + "/releases?per_page=20";
  private static final Pattern JAR_ASSET_PATTERN = Pattern.compile("(?i).*intave.*\\.jar$");
  private static final Pattern SHA_256_PATTERN = Pattern.compile("(?i)([a-f0-9]{64})");
  private static final String CACHE_JAR_NAME = "latest.jar";
  private static final String CACHE_META_NAME = "latest.properties";
  private static final int CONNECT_TIMEOUT_MILLIS = 3000;
  private static final int READ_TIMEOUT_MILLIS = 6000;
  private static final long SHUTDOWN_CHECK_BUDGET_MILLIS = 10000L;

  private final IntavePlugin plugin;
  private final ExecutorService startupExecutor;
  private final ReentrantLock cacheLock = new ReentrantLock();
  private Future<?> startupCheck;

  public AutoUpdate(IntavePlugin plugin) {
    this.plugin = plugin;
    this.startupExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory("Intave update check"));
  }

  public void onEnable() {
    final AutoUpdateConfig config = config();
    if (!config.enabled) {
      return;
    }
    startupCheck = startupExecutor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          checkAndCacheLatest(config, "startup", false);
        } catch (Throwable throwable) {
          warn("Update check failed during startup: " + throwable.getMessage());
        }
      }
    });
  }

  public void onDisable() {
    AutoUpdateConfig config = config();
    if (!config.enabled) {
      shutdownStartupExecutor();
      return;
    }
    runShutdownCheck(config);
    applyCachedUpdate(config);
    shutdownStartupExecutor();
  }

  private void runShutdownCheck(final AutoUpdateConfig config) {
    ExecutorService shutdownExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory("Intave shutdown update check"));
    Future<?> future = shutdownExecutor.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        checkAndCacheLatest(config, "shutdown", true);
        return null;
      }
    });
    try {
      future.get(SHUTDOWN_CHECK_BUDGET_MILLIS, TimeUnit.MILLISECONDS);
    } catch (TimeoutException exception) {
      future.cancel(true);
      warn("Update check exceeded " + SHUTDOWN_CHECK_BUDGET_MILLIS + "ms during shutdown; using existing cache");
    } catch (CancellationException ignored) {
    } catch (Exception exception) {
      warn("Update check failed during shutdown: " + exception.getMessage());
    } finally {
      shutdownExecutor.shutdownNow();
    }
  }

  private void checkAndCacheLatest(AutoUpdateConfig config, String reason, boolean waitForLock) throws IOException {
    boolean locked = false;
    try {
      try {
        locked = waitForLock ? cacheLock.tryLock(1L, TimeUnit.SECONDS) : cacheLock.tryLock();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return;
      }
      if (!locked) {
        if (waitForLock) {
          warn("Update cache is busy; skipping " + reason + " update check");
        }
        return;
      }
      Path currentJar = currentPluginJar();
      if (currentJar == null) {
        return;
      }
      String currentHash = sha256(currentJar);
      UpdateCandidate candidate = fetchLatestCandidate();
      if (candidate == null || candidate.expectedHash.equals(currentHash)) {
        return;
      }

      BasicFileAttributes attrs = Files.readAttributes(currentJar, BasicFileAttributes.class);
      long modifiedTime = attrs.lastModifiedTime().toMillis();
      long creationTime = attrs.creationTime().toMillis();

      // Take the older of the two timestamps to handle file copy scenarios
      long localTime = Math.min(modifiedTime, creationTime);
      if (candidate.updatedAtMillis <= localTime) {
        return;
      }

      CachedUpdate cachedUpdate = readCachedUpdate(config);
      if (cachedUpdate != null
        && cachedUpdate.matches(candidate)
        && Files.isRegularFile(cachedUpdate.jarFile)
        && cachedUpdate.jarHash.equals(sha256(cachedUpdate.jarFile))) {
        return;
      }
      cacheCandidate(candidate, config, reason);
    } finally {
      if (locked) {
        cacheLock.unlock();
      }
    }
  }

  private UpdateCandidate fetchLatestCandidate() throws IOException {
    JsonArray releases = readJsonArray(RELEASES_API);
    for (JsonElement releaseElement : releases) {
      if (!releaseElement.isJsonObject()) {
        continue;
      }
      JsonObject release = releaseElement.getAsJsonObject();
      if (booleanValue(release, "draft", false)) {
        continue;
      }
      JsonElement assetsElement = release.get("assets");
      if (assetsElement == null || !assetsElement.isJsonArray()) {
        continue;
      }
      JsonArray assets = assetsElement.getAsJsonArray();
      JsonObject jarAsset = selectReleaseJarAsset(assets);
      if (jarAsset == null) {
        continue;
      }
      String assetName = stringValue(jarAsset, "name", "Intave.jar");
      String downloadUrl = stringValue(jarAsset, "browser_download_url", null);
      if (downloadUrl == null) {
        continue;
      }
      String expectedHash = sha256FromDigest(jarAsset);
      if (expectedHash == null) {
        expectedHash = sha256FromHashAsset(assets, assetName);
      }
      if (expectedHash == null) {
        warn("GitHub release asset " + assetName + " has no SHA-256 digest; skipping update");
        continue;
      }

      long updatedAt = 0L;
      try {
        String updatedAtStr = stringValue(release, "updated_at", null);
        if (updatedAtStr != null) {
          updatedAt = Instant.parse(updatedAtStr).toEpochMilli();
        }
      } catch (Exception ignored) {
      }

      return new UpdateCandidate(
        stringValue(release, "tag_name", "release"),
        stringValue(release, "name", assetName),
        assetName,
        downloadUrl,
        expectedHash,
        updatedAt
      );
    }
    info("No public Intave release asset found for " + REPOSITORY);
    return null;
  }

  private void cacheCandidate(UpdateCandidate candidate, AutoUpdateConfig config, String reason) throws IOException {
    Files.createDirectories(config.cacheDirectory);
    String id = reason + "-" + UUID.randomUUID().toString();
    Path downloadFile = config.cacheDirectory.resolve(id + ".download");
    Path metadataFile = config.cacheDirectory.resolve(CACHE_META_NAME);
    Path metadataTemp = config.cacheDirectory.resolve(id + ".properties");
    Path targetJar = config.cacheDirectory.resolve(CACHE_JAR_NAME);
    try {
      download(candidate.downloadUrl, downloadFile);
      String downloadHash = sha256(downloadFile);
      if (!candidate.expectedHash.equals(downloadHash)) {
        throw new IOException("Downloaded update hash mismatch for " + candidate.assetName);
      }
      atomicReplace(downloadFile, targetJar);
      writeMetadata(candidate, downloadHash, metadataTemp);
      atomicReplace(metadataTemp, metadataFile);
      info("Cached Intave update " + candidate.tagName + " from " + reason);
    } finally {
      Files.deleteIfExists(downloadFile);
      Files.deleteIfExists(metadataTemp);
    }
  }

  private boolean applyCachedUpdate(AutoUpdateConfig config) {
    try {
      CachedUpdate cachedUpdate = readCachedUpdate(config);
      if (cachedUpdate == null || !Files.isRegularFile(cachedUpdate.jarFile)) {
        return false;
      }
      if (!cachedUpdate.jarHash.equals(sha256(cachedUpdate.jarFile))) {
        warn("Cached update hash mismatch; leaving current jar untouched");
        return false;
      }
      Path currentJar = currentPluginJar();
      if (currentJar == null) {
        return false;
      }
      String currentHash = sha256(currentJar);
      if (cachedUpdate.jarHash.equals(currentHash)) {
        return false;
      }
      Path tempTarget = currentJar.resolveSibling(currentJar.getFileName() + ".intave-update.tmp");
      Files.copy(cachedUpdate.jarFile, tempTarget, StandardCopyOption.REPLACE_EXISTING);
      if (!cachedUpdate.jarHash.equals(sha256(tempTarget))) {
        Files.deleteIfExists(tempTarget);
        warn("Copied update hash mismatch; leaving current jar untouched");
        return false;
      }
      try {
        atomicReplace(tempTarget, currentJar);
        info("Installed cached Intave update " + cachedUpdate.tagName + " for next boot");
        return true;
      } catch (IOException exception) {
        Files.deleteIfExists(tempTarget);
        return stageInBukkitUpdateFolder(cachedUpdate, currentJar);
      }
    } catch (Throwable throwable) {
      warn("Unable to apply cached update: " + throwable.getMessage());
      return false;
    }
  }

  private boolean stageInBukkitUpdateFolder(CachedUpdate cachedUpdate, Path currentJar) {
    try {
      File updateFolder = plugin.getServer().getUpdateFolderFile();
      Path updateFolderPath = updateFolder.toPath();
      Files.createDirectories(updateFolderPath);
      Path stagedJar = updateFolderPath.resolve(currentJar.getFileName());
      Path tempStagedJar = updateFolderPath.resolve(currentJar.getFileName() + ".intave-update.tmp");
      Files.copy(cachedUpdate.jarFile, tempStagedJar, StandardCopyOption.REPLACE_EXISTING);
      atomicReplace(tempStagedJar, stagedJar);
      info("Staged cached Intave update " + cachedUpdate.tagName + " in " + updateFolder.getPath());
      return true;
    } catch (IOException exception) {
      warn("Could not replace or stage cached Intave update: " + exception.getMessage());
      return false;
    }
  }

  private CachedUpdate readCachedUpdate(AutoUpdateConfig config) throws IOException {
    Path metadataFile = config.cacheDirectory.resolve(CACHE_META_NAME);
    Path jarFile = config.cacheDirectory.resolve(CACHE_JAR_NAME);
    if (!Files.isRegularFile(metadataFile) || !Files.isRegularFile(jarFile)) {
      return null;
    }
    Properties properties = new Properties();
    InputStream inputStream = null;
    try {
      inputStream = new BufferedInputStream(new FileInputStream(metadataFile.toFile()));
      properties.load(inputStream);
    } finally {
      closeQuietly(inputStream);
    }
    String jarHash = normalizedSha256(properties.getProperty("jar-sha256"));
    if (jarHash == null) {
      return null;
    }
    return new CachedUpdate(
      properties.getProperty("tag", "release"),
      properties.getProperty("asset", jarFile.getFileName().toString()),
      properties.getProperty("url", ""),
      jarHash,
      jarFile
    );
  }

  private void writeMetadata(UpdateCandidate candidate, String jarHash, Path metadataFile) throws IOException {
    Properties properties = new Properties();
    properties.setProperty("tag", candidate.tagName);
    properties.setProperty("name", candidate.releaseName);
    properties.setProperty("asset", candidate.assetName);
    properties.setProperty("url", candidate.downloadUrl);
    properties.setProperty("jar-sha256", jarHash);
    properties.setProperty("downloaded-at", Long.toString(System.currentTimeMillis()));
    OutputStream outputStream = null;
    try {
      outputStream = new BufferedOutputStream(new FileOutputStream(metadataFile.toFile()));
      properties.store(outputStream, "Intave update cache");
    } finally {
      closeQuietly(outputStream);
    }
  }

  private JsonObject selectReleaseJarAsset(JsonArray assets) {
    JsonObject fallback = null;
    for (JsonElement element : assets) {
      if (!element.isJsonObject()) {
        continue;
      }
      JsonObject asset = element.getAsJsonObject();
      String name = stringValue(asset, "name", "");
      String lowerName = name.toLowerCase(Locale.ROOT);
      if (!lowerName.endsWith(".jar") || lowerName.contains("sources") || lowerName.contains("javadoc")) {
        continue;
      }
      if (JAR_ASSET_PATTERN.matcher(name).matches()) {
        return asset;
      }
      if (fallback == null) {
        fallback = asset;
      }
    }
    return fallback;
  }

  private Path currentPluginJar() throws IOException {
    try {
      URI uri = plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
      Path path = new File(uri).toPath();
      if (!Files.isRegularFile(path) || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
        return null;
      }
      return path;
    } catch (Exception exception) {
      throw new IOException("Unable to resolve current Intave jar", exception);
    }
  }

  private JsonArray readJsonArray(String url) throws IOException {
    InputStream inputStream = null;
    try {
      inputStream = openConnection(url, "application/vnd.github+json").getInputStream();
      JsonElement element = new JsonParser().parse(new InputStreamReader(inputStream, "UTF-8"));
      if (!element.isJsonArray()) {
        throw new IOException("Expected JSON array from " + url);
      }
      return element.getAsJsonArray();
    } finally {
      closeQuietly(inputStream);
    }
  }

  private String readText(String url) throws IOException {
    if (url == null) {
      return null;
    }
    InputStream inputStream = null;
    try {
      inputStream = openConnection(url, "text/plain").getInputStream();
      InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8");
      char[] buffer = new char[4096];
      StringBuilder output = new StringBuilder();
      int read;
      while ((read = reader.read(buffer)) != -1) {
        output.append(buffer, 0, read);
      }
      return output.toString();
    } finally {
      closeQuietly(inputStream);
    }
  }

  private void download(String url, Path targetFile) throws IOException {
    InputStream inputStream = null;
    OutputStream outputStream = null;
    try {
      inputStream = openConnection(url, "application/octet-stream").getInputStream();
      outputStream = new BufferedOutputStream(new FileOutputStream(targetFile.toFile()));
      copy(inputStream, outputStream);
    } finally {
      closeQuietly(outputStream);
      closeQuietly(inputStream);
    }
  }

  private HttpURLConnection openConnection(String url, String accept) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setInstanceFollowRedirects(true);
    connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
    connection.setReadTimeout(READ_TIMEOUT_MILLIS);
    connection.setRequestProperty("Accept", accept);
    connection.setRequestProperty("User-Agent", "Intave-UpdateLoader/" + plugin.getDescription().getVersion());
    int responseCode = connection.getResponseCode();
    if (responseCode < 200 || responseCode >= 300) {
      closeQuietly(connection.getErrorStream());
      throw new IOException("HTTP " + responseCode + " from " + url);
    }
    return connection;
  }

  private String sha256(Path file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      InputStream inputStream = null;
      try {
        inputStream = new BufferedInputStream(new FileInputStream(file.toFile()));
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
          digest.update(buffer, 0, read);
        }
      } finally {
        closeQuietly(inputStream);
      }
      return hex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
    byte[] buffer = new byte[8192];
    int read;
    while ((read = inputStream.read(buffer)) != -1) {
      outputStream.write(buffer, 0, read);
    }
  }

  private String hex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      builder.append(String.format("%02x", value & 0xFF));
    }
    return builder.toString();
  }

  private String sha256FromDigest(JsonObject object) {
    String digest = stringValue(object, "digest", null);
    if (digest == null) {
      return null;
    }
    String normalized = digest.toLowerCase(Locale.ROOT);
    if (normalized.startsWith("sha256:")) {
      normalized = normalized.substring("sha256:".length());
    }
    return validSha256(normalized) ? normalized : null;
  }

  private String sha256FromHashAsset(JsonArray assets, String jarAssetName) throws IOException {
    JsonObject fallback = null;
    String jarNameLower = jarAssetName.toLowerCase(Locale.ROOT);
    for (JsonElement element : assets) {
      if (!element.isJsonObject()) {
        continue;
      }
      JsonObject asset = element.getAsJsonObject();
      String name = stringValue(asset, "name", "");
      String lowerName = name.toLowerCase(Locale.ROOT);
      boolean hashAsset = lowerName.endsWith(".sha256")
        || lowerName.endsWith(".sha256sum")
        || lowerName.endsWith(".sha256.txt");
      if (!hashAsset) {
        continue;
      }
      if (lowerName.contains(jarNameLower) || jarNameLower.contains(lowerName.replace(".sha256", ""))) {
        return extractSha256(readText(stringValue(asset, "browser_download_url", null)));
      }
      if (fallback == null) {
        fallback = asset;
      }
    }
    return fallback == null ? null : extractSha256(readText(stringValue(fallback, "browser_download_url", null)));
  }

  private String extractSha256(String text) {
    if (text == null) {
      return null;
    }
    Matcher matcher = SHA_256_PATTERN.matcher(text);
    return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
  }

  private void atomicReplace(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private boolean validSha256(String value) {
    return value != null && SHA_256_PATTERN.matcher(value).matches();
  }

  private String normalizedSha256(String value) {
    return validSha256(value) ? value.toLowerCase(Locale.ROOT) : null;
  }

  private String stringValue(JsonObject object, String key, String fallback) {
    JsonElement element = object.get(key);
    if (element == null || element.isJsonNull()) {
      return fallback;
    }
    try {
      return element.getAsString();
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }

  private boolean booleanValue(JsonObject object, String key, boolean fallback) {
    JsonElement element = object.get(key);
    if (element == null || element.isJsonNull()) {
      return fallback;
    }
    try {
      return element.getAsBoolean();
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }

  private AutoUpdateConfig config() {
    YamlConfiguration settings = plugin.settings();
    return new AutoUpdateConfig(
      settings.getBoolean(CONFIG_ROOT + ".enabled", true),
      plugin.dataFolder().toPath().resolve("updates")
    );
  }

  private void shutdownStartupExecutor() {
    if (startupCheck != null && !startupCheck.isDone()) {
      startupCheck.cancel(true);
    }
    startupExecutor.shutdownNow();
  }

  private ThreadFactory daemonThreadFactory(final String name) {
    return new ThreadFactory() {
      @Override
      public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
      }
    };
  }

  private void closeQuietly(InputStream inputStream) {
    if (inputStream == null) {
      return;
    }
    try {
      inputStream.close();
    } catch (IOException ignored) {
    }
  }

  private void closeQuietly(OutputStream outputStream) {
    if (outputStream == null) {
      return;
    }
    try {
      outputStream.close();
    } catch (IOException ignored) {
    }
  }

  private void info(String message) {
    plugin.logger().info(message);
  }

  private void warn(String message) {
    plugin.logger().warn(message);
  }

  private static final class AutoUpdateConfig {
    private final boolean enabled;
    private final Path cacheDirectory;

    private AutoUpdateConfig(boolean enabled, Path cacheDirectory) {
      this.enabled = enabled;
      this.cacheDirectory = cacheDirectory;
    }
  }

  private static final class UpdateCandidate {
    private final String tagName;
    private final String releaseName;
    private final String assetName;
    private final String downloadUrl;
    private final String expectedHash;
    private final long updatedAtMillis;

    private UpdateCandidate(String tagName, String releaseName, String assetName, String downloadUrl, String expectedHash, long updatedAtMillis) {
      this.tagName = tagName;
      this.releaseName = releaseName;
      this.assetName = assetName;
      this.downloadUrl = downloadUrl;
      this.expectedHash = expectedHash.toLowerCase(Locale.ROOT);
      this.updatedAtMillis = updatedAtMillis;
    }
  }

  private static final class CachedUpdate {
    private final String tagName;
    private final String assetName;
    private final String downloadUrl;
    private final String jarHash;
    private final Path jarFile;

    private CachedUpdate(String tagName, String assetName, String downloadUrl, String jarHash, Path jarFile) {
      this.tagName = tagName;
      this.assetName = assetName;
      this.downloadUrl = downloadUrl;
      this.jarHash = jarHash;
      this.jarFile = jarFile;
    }

    private boolean matches(UpdateCandidate candidate) {
      return jarHash.equals(candidate.expectedHash)
        && stringEquals(downloadUrl, candidate.downloadUrl);
    }

    private boolean stringEquals(String first, String second) {
      return first == null ? second == null : first.equals(second);
    }
  }
}
