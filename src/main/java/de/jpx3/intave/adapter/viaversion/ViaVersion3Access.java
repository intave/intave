package de.jpx3.intave.adapter.viaversion;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public final class ViaVersion3Access implements ViaVersionAccess {
  private Object viaVersionTarget;
  private Method getPlayerVersionMethod;

  @Override
  public void setup() {
    try {
      Class<?> apiAcessorClass = Class.forName("us.myles.ViaVersion.api.Via");
      this.viaVersionTarget = apiAcessorClass.getMethod("getAPI").invoke(null);
      this.getPlayerVersionMethod = Class.forName("us.myles.ViaVersion.api.ViaAPI").getMethod("getPlayerVersion", UUID.class);
    } catch (Exception exception) {
      throw new IllegalStateException("Invalid ViaVersion linkage", exception);
    }
  }

  @Override
  public void patchConfiguration() {
    try {
      Class<?> viaVersion = Class.forName("us.myles.ViaVersion.ViaVersionPlugin");
      Object configuration = viaVersion.getMethod("getConfigurationProvider").invoke(Bukkit.getPluginManager().getPlugin("ViaVersion"));
      Class<?> configurationClass = Class.forName("us.myles.ViaVersion.AbstractViaConfig");
      Field maxPPSField = configurationClass.getDeclaredField("warningPPS");
      maxPPSField.setAccessible(true);
      int maxpps = maxPPSField.getInt(configuration);
      maxPPSField.set(configuration, Math.max(maxpps, 600));
    } catch (Exception exception) {
      warnPatchConfigurationFailure();
    }
  }

  @Override
  public int protocolVersionOf(Player player) {
    try {
      return (int) getPlayerVersionMethod.invoke(viaVersionTarget, player.getUniqueId());
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
    return version.startsWith("3");
  }

  @Override
  public String version() {
    return Bukkit.getPluginManager().getPlugin("ViaVersion").getDescription().getVersion();
  }
}
