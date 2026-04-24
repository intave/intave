package de.jpx3.intave.adapter.viaversion;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketTracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public final class ViaVersion4Access implements ViaVersionAccess {
  private Object viaVersionTarget;
  private Method getPlayerVersionMethod;

  @Override
  public void setup() {
    try {
      Class<?> apiAcessorClass = Class.forName("com.viaversion.viaversion.api.Via");
      this.viaVersionTarget = apiAcessorClass.getMethod("getAPI").invoke(null);
      this.getPlayerVersionMethod = Class.forName("com.viaversion.viaversion.api.ViaAPI").getMethod("getPlayerVersion", UUID.class);
    } catch (Exception exception) {
      throw new IllegalStateException("Invalid ViaVersion linkage", exception);
    }
  }

  @Override
  public void patchConfiguration() {
    try {
      Class<?> viaVersion = Class.forName("com.viaversion.viaversion.ViaVersionPlugin");
      Object configuration = viaVersion.getMethod("getConfigurationProvider").invoke(Bukkit.getPluginManager().getPlugin("ViaVersion"));
      Class<?> configurationClass = Class.forName("com.viaversion.viaversion.configuration.AbstractViaConfig");
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
  public void decrementReceivedPackets(Player player, int amount) {
    UserConnection connection = Via.getAPI().getConnection(player.getUniqueId());
    if (connection == null) {
      return;
    }
    PacketTracker packetTracker = connection.getPacketTracker();
    packetTracker.setIntervalPackets(Math.max(packetTracker.getIntervalPackets() - amount, 0));
  }

  @Override
  public boolean ignoreBlocking(Player player) {
    return false;
  }

  @Override
  public boolean available(String version) {
    return version.startsWith("4") && !version.startsWith("4.9") && !version.startsWith("4.1");
  }

  @Override
  public String version() {
    return Bukkit.getPluginManager().getPlugin("ViaVersion").getDescription().getVersion();
  }
}
