/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.adapter;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.connect.IntaveDomains;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipFile;

public final class ComponentLoader {
  private static final int DOWNLOAD_RETRIES = 4;
  private static final long INITIAL_RETRY_DELAY_MILLIS = 1_000L;
  private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
  private static final int READ_TIMEOUT_MILLIS = 30_000;
  private static final String PAPER_RUNTIME_BAN_MESSAGE = "cannot register paper plugins during runtime";

  static final String PROTOCOL_LIB_DEV_URL = "https://github.com/dmulloy2/ProtocolLib/releases/download/dev-build/ProtocolLib.jar";
  static final String PROTOCOL_LIB_STABLE_URL = "https://github.com/dmulloy2/ProtocolLib/releases/download/5.4.0/ProtocolLib.jar";

  public Map<String, String> essentialComponents = new HashMap<>();
  private final IntavePlugin plugin;

  public ComponentLoader(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void prepareComponents() {
    essentialComponents.put("ProtocolLib", selectProtocolLibUrl(Bukkit.getVersion(), legacyProtocolLibUrl()));
  }

  // numeric comparison, calendar versions (26.x and later) always use the dev build
  static String selectProtocolLibUrl(String serverVersion, String legacyUrl) {
    try {
      MinecraftVersion version = MinecraftVersion.fromServerVersion(serverVersion);
      if (version.getMajor() >= 26) {
        return PROTOCOL_LIB_DEV_URL;
      }
      if (version.getMajor() == 1) {
        if (version.getMinor() == 21) {
          return version.getBuild() >= 10 ? PROTOCOL_LIB_DEV_URL : PROTOCOL_LIB_STABLE_URL;
        }
        if (version.getMinor() >= 19) {
          return PROTOCOL_LIB_STABLE_URL;
        }
      }
      return legacyUrl;
    } catch (Exception unparseable) {
      // unknown version string, assume a recent server and try the newest build
      return PROTOCOL_LIB_DEV_URL;
    }
  }

  private static String legacyProtocolLibUrl() {
    return "https://" + IntaveDomains.primaryServiceDomain() + "/resource/ProtocolLib-4-8-0.jar";
  }

  public void loadComponents() {
    for (String component : essentialComponents.keySet()) {
      try {
        loadComponent(component);
      } catch (ComponentRestartRequiredException restart) {
        // not a failure, just needs a reboot, goes straight to the caller
        throw restart;
      } catch (Exception exception) {
        throw new IntaveInternalException("Unable to load library " + component, exception);
      }
    }
  }

  private void loadComponent(String componentName) {
    Plugin componentPlugin = Bukkit.getPluginManager().getPlugin(componentName);
    if (componentPlugin != null) {
      if (!componentPlugin.isEnabled()) {
        Bukkit.getPluginManager().enablePlugin(componentPlugin);
      }
      return;
    }

    File componentPluginFile = componentFile(componentName);
    if (!componentPluginFile.exists()) {
      downloadComponentJar(componentPluginFile, componentName, essentialComponents.get(componentName));
    }
    // file on disk but nothing loaded yet (fresh download or stale jar), activate it now
    activateComponentJar(componentPluginFile, componentName);
  }

  private File componentFile(String componentName) {
    return new File(plugin.dataFolder().getParentFile(), componentName + ".jar");
  }

  private void downloadComponentJar(File target, String componentName, String downloadURL) {
    try {
      downloadWithRetries(new URL(downloadURL), target.toPath(), componentName);
      validatePluginJar(target.toPath(), componentName);
    } catch (IOException exception) {
      throw new IntaveInternalException("Unable to download library " + componentName, exception);
    }
    plugin.logger().info(ChatColor.GREEN + "Downloaded " + componentName);
  }

  private void activateComponentJar(File componentPluginFile, String componentName) {
    try {
      Plugin componentPlugin = plugin.getServer().getPluginManager().loadPlugin(componentPluginFile);
      plugin.getServer().getPluginManager().enablePlugin(componentPlugin);
    } catch (InvalidPluginException | InvalidDescriptionException exception) {
      if (isPaperRuntimeBan(exception)) {
        // jar is fine, paper just refuses paper plugins loaded after startup, reboot once
        throw new ComponentRestartRequiredException(componentName, componentPluginFile.getName(), exception);
      }
      throw new IntaveInternalException("Unable to load library " + componentName, exception);
    }
  }

  // walks the cause chain because bukkit wraps the paper error twice
  static boolean isPaperRuntimeBan(Throwable throwable) {
    while (throwable != null) {
      String message = throwable.getMessage();
      if (message != null && message.toLowerCase(Locale.ROOT).contains(PAPER_RUNTIME_BAN_MESSAGE)) {
        return true;
      }
      throwable = throwable.getCause();
    }
    return false;
  }

  // error pages and truncated downloads are not jars, drop them before they poison later boots
  static void validatePluginJar(Path jar, String componentName) throws IOException {
    if (!isPluginJar(jar)) {
      Files.deleteIfExists(jar);
      throw new IOException("downloaded file for " + componentName + " is not a valid plugin jar");
    }
  }

  static boolean isPluginJar(Path jar) {
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      return zip.getEntry("plugin.yml") != null || zip.getEntry("paper-plugin.yml") != null;
    } catch (IOException notAJar) {
      return false;
    }
  }

  private void downloadWithRetries(URL website, Path target, String componentName) throws IOException {
    for (int retry = 0; ; retry++) {
      try {
        download(website, target);
        return;
      } catch (IOException exception) {
        if (retry == DOWNLOAD_RETRIES) {
          throw exception;
        }
        long delayMillis = INITIAL_RETRY_DELAY_MILLIS << retry;
        plugin.logger().warn("Unable to download " + componentName + ": " + exception.getMessage()
          + ". Retrying in " + delayMillis / 1_000L + " seconds (retry " + (retry + 1) + "/" + DOWNLOAD_RETRIES + ")");
        try {
          Thread.sleep(delayMillis);
        } catch (InterruptedException interruptedException) {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted while retrying download of " + componentName, interruptedException);
        }
      }
    }
  }

  private void download(URL website, Path target) throws IOException {
    URLConnection connection = website.openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
    connection.setReadTimeout(READ_TIMEOUT_MILLIS);
    connection.setRequestProperty("User-Agent", "Intave/" + IntavePlugin.fullVersion());
    if (connection instanceof HttpURLConnection) {
      int responseCode = ((HttpURLConnection) connection).getResponseCode();
      if (responseCode != HttpURLConnection.HTTP_OK) {
        throw new IOException("unexpected response " + responseCode + " while downloading from " + website);
      }
    }
    Path temporaryFile = Files.createTempFile(target.toAbsolutePath().getParent(), target.getFileName().toString(), ".tmp");
    try {
      try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(temporaryFile)) {
        copy(in, out);
      }
      Files.move(temporaryFile, target);
    } finally {
      Files.deleteIfExists(temporaryFile);
    }
  }

  private void copy(InputStream source, OutputStream sink) throws IOException {
    byte[] buf = new byte[8192];
    int n;
    while ((n = source.read(buf)) > 0) {
      sink.write(buf, 0, n);
    }
  }
}
