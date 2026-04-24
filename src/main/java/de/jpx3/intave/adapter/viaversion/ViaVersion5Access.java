package de.jpx3.intave.adapter.viaversion;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketTracker;
import de.jpx3.intave.IntaveLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class ViaVersion5Access implements ViaVersionAccess {
  private Class<?> apiAccessorClass;
  private Object viaVersionTarget;
  private Method getPlayerVersionMethod;

  @Override
  public void setup() {
    try {
      this.apiAccessorClass = Class.forName("com.viaversion.viaversion.api.Via");
      this.viaVersionTarget = apiAccessorClass.getMethod("getAPI").invoke(null);
      this.getPlayerVersionMethod = Class.forName("com.viaversion.viaversion.api.ViaAPI").getMethod("getPlayerVersion", UUID.class);
    } catch (Exception exception) {
      throw new IllegalStateException("Invalid ViaVersion linkage", exception);
    }
  }

  @Override
  public void patchConfiguration() {
    try {
      Object config = apiAccessorClass.getMethod("getConfig").invoke(null);
      Class<?> configurationClass = Class.forName("com.viaversion.viaversion.configuration.AbstractViaConfig");
      boolean patchedPacketLimits = patchLegacyPacketLimitFields(config, configurationClass) || patchRateLimitConfig(config, configurationClass);
      try {
        Field fix121PlacementField = configurationClass.getDeclaredField("fix1_21PlacementRotation");
        fix121PlacementField.setAccessible(true);
        if (fix121PlacementField.getBoolean(config)) {
          fix121PlacementField.set(config, false);
          IntaveLogger.logger().info("Disabled ViaVersion 1.21 placement rotation fix");
        }
      } catch (ReflectiveOperationException ignored) {
      }
      if (!patchedPacketLimits) {
        warnPatchConfigurationFailure();
      }
    } catch (Exception exception) {
      warnPatchConfigurationFailure();
    }
  }

  private boolean patchLegacyPacketLimitFields(Object config, Class<?> configurationClass) throws IllegalAccessException {
    boolean patched = false;
    patched |= tryPatchLegacyField(config, configurationClass, "warningPPS");
    patched |= tryPatchLegacyField(config, configurationClass, "maxPPS");
    return patched;
  }

  private boolean tryPatchLegacyField(Object config, Class<?> configurationClass, String fieldName) throws IllegalAccessException {
    try {
      Field field = configurationClass.getDeclaredField(fieldName);
      field.setAccessible(true);
      int currentValue = field.getInt(config);
      field.setInt(config, relaxedLimit(currentValue));
      return true;
    } catch (NoSuchFieldException ignored) {
      return false;
    }
  }

  private boolean patchRateLimitConfig(Object config, Class<?> configurationClass) throws ReflectiveOperationException {
    Field packetTrackerConfigField = configurationClass.getDeclaredField("packetTrackerConfig");
    packetTrackerConfigField.setAccessible(true);
    Object packetTrackerConfig = packetTrackerConfigField.get(config);
    if (packetTrackerConfig == null) {
      return false;
    }

    Class<?> rateLimitConfigClass = Class.forName("com.viaversion.viaversion.api.configuration.RateLimitConfig");
    Constructor<?> constructor = rateLimitConfigClass.getDeclaredConstructor(
      boolean.class,
      int.class,
      String.class,
      int.class,
      int.class,
      long.class,
      String.class,
      String.class
    );
    Object patchedConfig = constructor.newInstance(
      rateLimitConfigClass.getMethod("enabled").invoke(packetTrackerConfig),
      relaxedLimit((int) rateLimitConfigClass.getMethod("maxRate").invoke(packetTrackerConfig)),
      rateLimitConfigClass.getMethod("maxRateKickMessage").invoke(packetTrackerConfig),
      relaxedLimit((int) rateLimitConfigClass.getMethod("warningRate").invoke(packetTrackerConfig)),
      rateLimitConfigClass.getMethod("maxWarnings").invoke(packetTrackerConfig),
      rateLimitConfigClass.getMethod("trackingPeriodNanos").invoke(packetTrackerConfig),
      rateLimitConfigClass.getMethod("warningKickMessage").invoke(packetTrackerConfig),
      rateLimitConfigClass.getMethod("ratePlaceholder").invoke(packetTrackerConfig)
    );
    packetTrackerConfigField.set(config, patchedConfig);
    return true;
  }

  private int relaxedLimit(int currentValue) {
    return currentValue < 0 ? currentValue : Math.max(currentValue, 600);
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
  public void decrementReceivedPackets(Player player, int amount) {
    UserConnection connection = Via.getAPI().getConnection(player.getUniqueId());
    if (connection == null) {
      return;
    }
    PacketTracker packetTracker = connection.getPacketTracker();
    packetTracker.setIntervalPackets(Math.max(packetTracker.getIntervalPackets() - amount, 0));
  }

  @Override
  public boolean available(String version) {
    List<String> supportedVersions = Arrays.asList("4.1", "4.9", "5");
    return supportedVersions.stream().anyMatch(version::startsWith);
  }

  @Override
  public String version() {
    return Bukkit.getPluginManager().getPlugin("ViaVersion").getDescription().getVersion();

  }
}
