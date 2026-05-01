package de.jpx3.intave.adapter;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import de.jpx3.intave.IntaveControl;
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
import java.net.URL;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public final class ComponentLoader {
  public Map<String, String> essentialComponents = new HashMap<>();
  private final IntavePlugin plugin;

  public ComponentLoader(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void prepareComponents() {
    String version = Bukkit.getVersion();
    if (version.contains("MC: 1.21.11") || version.contains("MC: 1.21.10") || version.contains("MC: 26.1") || version.contains("MC: 26.2")) {
      essentialComponents.put("ProtocolLib", "https://github.com/dmulloy2/ProtocolLib/releases/download/dev-build/ProtocolLib.jar");
    } else if (version.contains("MC: 1.19") || version.contains("MC: 1.20") || version.contains("MC: 1.21")) {
      essentialComponents.put("ProtocolLib", "https://github.com/dmulloy2/ProtocolLib/releases/download/5.4.0/ProtocolLib.jar");
    } else {
      essentialComponents.put("ProtocolLib", "https://" + IntaveDomains.primaryServiceDomain() + "/resource/ProtocolLib-4-8-0.jar");
    }
  }

  public void loadComponents() {
    for (String component : essentialComponents.keySet()) {
      try {
        if (!loadComponent(component)) {
          return;
        }
      } catch (Exception exception) {
        throw new IntaveInternalException("Unable to load library " + component, exception);
      }
    }
  }

  private boolean loadComponent(String componentName) {
    Plugin componentPlugin = Bukkit.getPluginManager().getPlugin(componentName);
    if (componentPlugin != null) {
      if (!componentPlugin.isEnabled()) {
        Bukkit.getPluginManager().enablePlugin(componentPlugin);
      }
      return false;
    }

    File componentPluginFile = new File(plugin.dataFolder().getParentFile().getAbsolutePath() + "/" + componentName + ".jar");
    if (!componentPluginFile.exists()) {
      String downloadURL = this.essentialComponents.get(componentName);
      try {
        downloadComponentPlugin(componentPluginFile, componentName, downloadURL);
        return true;
      } catch (Exception exception) {
        throw new IntaveInternalException("Unable to download library " + componentName, exception);
      }
    }
    return false;
  }

  private void downloadComponentPlugin(File componentPluginFile, String componentName, String downloadURL) throws IOException, InvalidPluginException, InvalidDescriptionException {
    URL website = new URL(downloadURL);
    System.out.println("[debug] Downloading " + componentName + " from " + downloadURL);
    try (InputStream in = website.openStream()) {
      download(in, componentPluginFile.toPath());
      plugin.logger().info(ChatColor.GREEN + "Downloaded " + componentName);
      Plugin componentPlugin = this.plugin.getServer().getPluginManager().loadPlugin(componentPluginFile);
      ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
      if (protocolManager == null) {
        try {
          componentPlugin.onLoad();
        } catch (Throwable throwable) {
          // ProtocolLib Moment
        }
      }
      this.plugin.getServer().getPluginManager().enablePlugin(componentPlugin);
    }
  }

  private void download(InputStream in, Path target) throws IOException {
    OutputStream ostream;
    ostream = newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    try (OutputStream out = ostream) {
      copy(in, out);
    }
  }

  private void copy(InputStream source, OutputStream sink) throws IOException {
    byte[] buf = new byte[4096];
    int n, nStart = -1;
    while ((n = source.read(buf)) > 0) {
      if (nStart < 0) {
        nStart = n;
      }
      sink.write(buf, 0, n);
    }
  }

  private OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
    return path.getFileSystem().provider().newOutputStream(path, options);
  }
}
