package de.jpx3.intave.adapter;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
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

  public boolean loadComponents() {
    essentialComponents.put("ProtocolLib", "https://service.intave.de/resource/ProtocolLib-4-7-1.jar");
    for (String s : essentialComponents.keySet()) {
      try {
        if (!loadComponent(s)) {
          return false;
        }
      } catch (Exception exception) {
        throw new IntaveInternalException("Unable to load library " + s, exception);
      }
    }
    return true;
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
      } catch (Exception e) {
        throw new IntaveInternalException("Unable to download library " + componentName, e);
      }
    }
    return false;
  }

  private void downloadComponentPlugin(File componentPluginFile, String componentName, String downloadURL) throws IOException, InvalidPluginException, InvalidDescriptionException {
    URL website = new URL(downloadURL);
    try (InputStream in = website.openStream()) {
      download(in, componentPluginFile.toPath());
      plugin.logger().info(ChatColor.GREEN + "Downloaded " + componentName);
      Plugin compPlug = plugin.getServer().getPluginManager().loadPlugin(componentPluginFile);
      compPlug.onLoad();
      plugin.getServer().getPluginManager().enablePlugin(compPlug);
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
