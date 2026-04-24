package de.jpx3.intave.adapter;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.access.InvalidDependencyException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

public final class ComponentLoader {
  public Map<String, String> essentialComponents = new HashMap<>();
  private final IntavePlugin plugin;

  public ComponentLoader(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void prepareComponents() {
    essentialComponents.put("packetevents", null);
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
    if (componentPlugin == null) {
      throw new InvalidDependencyException("Missing required plugin " + componentName + ". Install PacketEvents as a normal (non-shaded) server plugin.");
    }
    if (!componentPlugin.isEnabled()) {
      Bukkit.getPluginManager().enablePlugin(componentPlugin);
    }
    return false;
  }
}
