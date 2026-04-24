package de.jpx3.intave.adapter.viaversion;

import de.jpx3.intave.IntaveLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class ViaVersion2Access implements ViaVersionAccess {
  private Object viaVersionInstance;
  private Method getPlayerVersionMethod;

  @Override
  public void setup() {
    try {
      Class<?> viaVersion = Class.forName("us.myles.ViaVersion.api.ViaVersion");
      viaVersionInstance = viaVersion.getMethod("getInstance").invoke(null);
      getPlayerVersionMethod = viaVersionInstance.getClass().getMethod("getPlayerVersion", UUID.class);
    } catch (Exception exception) {
      throw new IllegalStateException("Invalid ViaVersion linkage", exception);
    }
    IntaveLogger.logger().info("You are running a very old, outdated version of ViaVersion");
  }

  @Override
  public void patchConfiguration() {
    try {
      Class<?> viaVersion = Class.forName("us.myles.ViaVersion.ViaVersionPlugin");
      Object configuration = viaVersion.getMethod("getConfigurationProvider").invoke(Bukkit.getPluginManager().getPlugin("ViaVersion"));
      Class<?> configurationClass = Class.forName("us.myles.ViaVersion.api.configuration.ConfigurationProvider");
      configurationClass.getMethod("set", String.class, Object.class).invoke(configuration, "tracking-warning-pps", 300);
    } catch (Exception exception) {
      warnPatchConfigurationFailure();
    }
  }

  @Override
  public int protocolVersionOf(Player player) {
    try {
      return (int) getPlayerVersionMethod.invoke(viaVersionInstance, player.getUniqueId());
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to resolve player version", exception);
    }
  }

  @Override
  public boolean ignoreBlocking(Player player) {
    return false;
  }

  @Override
  public boolean available(String version) {
    return version.startsWith("2");
  }

  @Override
  public String version() {
    return ((JavaPlugin) viaVersionInstance).getDescription().getVersion();
  }
}
