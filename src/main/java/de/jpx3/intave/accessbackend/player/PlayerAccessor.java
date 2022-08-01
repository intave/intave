package de.jpx3.intave.accessbackend.player;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.check.Check;
import de.jpx3.intave.access.check.UnknownCheckException;
import de.jpx3.intave.access.player.PlayerAccess;
import de.jpx3.intave.access.player.PlayerClicks;
import de.jpx3.intave.access.player.PlayerConnection;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.cleanup.ReferenceMap;
import de.jpx3.intave.klass.trace.Caller;
import de.jpx3.intave.klass.trace.PluginInvocation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PlayerAccessor {
  private final IntavePlugin plugin;
  private final Map<UUID, PlayerAccess> playerAccessCache = GarbageCollector.watch(ReferenceMap.soft(Maps.newConcurrentMap()));
  private final PlayerNetStatisticsAccessor netStatisticsAccessor;
  private final PlayerClickStatisticsAccessor clickStatisticsAccessor;

  public PlayerAccessor(IntavePlugin plugin) {
    this.plugin = plugin;
    this.netStatisticsAccessor = new PlayerNetStatisticsAccessor(plugin);
    this.clickStatisticsAccessor = new PlayerClickStatisticsAccessor(plugin);
  }

  public synchronized PlayerAccess playerAccessOf(Player player) {
    Preconditions.checkNotNull(player);
    return playerAccessCache.computeIfAbsent(player.getUniqueId(), uuid -> newPlayerAccess(player));
  }

  private static final Map<String, Double> DEFAULT_RETURN = new HashMap<>();

  private PlayerAccess newPlayerAccess(Player player) {
    User user = UserRepository.userOf(player);
    Map<String, Map<String, Double>> violationLevel = user.meta().violationLevel().violationLevel;

    return new PlayerAccess() {
      @Override
      public int protocolVersion() {
        return user.protocolVersion();
      }

      @Override
      public void setProtocolVersion(int paramInt) {
        String message = "Changed protocol-version of " + player.getName() + " to " + paramInt + " (unknown origin)";
        IntaveLogger.logger().info(message);
        user.meta().protocol().setProtocolVersion(paramInt);
        user.applyNewProtocolVersion();
      }

      @Override
      public double violationLevel(String check, String threshold) {
        check = check.toLowerCase(Locale.ROOT);
        if (!plugin.checks().hasCheck(check)) {
          throw new UnknownCheckException("Unable to locate check \"" + check + "\"");
        }
        return violationLevel.getOrDefault(check, DEFAULT_RETURN).getOrDefault(threshold, 0d);
      }

      @Override
      public double violationLevel(Check check, String threshold) {
        return violationLevel(check.typeName(), threshold);
      }

      @Override
      public void addViolationPoints(String check, String threshold, double amount) {
        check = check.toLowerCase(Locale.ROOT);
        if (!plugin.checks().hasCheck(check)) {
          throw new UnknownCheckException("Unable to locate check \"" + check + "\"");
        }
        violationLevel.getOrDefault(check, DEFAULT_RETURN).put(threshold, violationLevel(check, threshold) + amount);
      }

      @Override
      public void addViolationPoints(Check check, String threshold, double amount) {
        addViolationPoints(check.typeName(), threshold, amount);
      }

      @Override
      public void resetViolationLevel(String check, String threshold) {
        check = check.toLowerCase(Locale.ROOT);
        if (!plugin.checks().hasCheck(check)) {
          throw new UnknownCheckException("Unable to locate check \"" + check + "\"");
        }
        violationLevel.getOrDefault(check, DEFAULT_RETURN).remove(threshold);
      }

      @Override
      public void resetViolationLevel(Check check, String threshold) {
        resetViolationLevel(check.typeName(), threshold);
      }

      @Override
      public TrustFactor trustFactor() {
        return user.trustFactor();
      }

      @Override
      public void setTrustFactor(TrustFactor factor) {
        PluginInvocation pluginInvocation = Caller.pluginInfo();
        String message;
        if (pluginInvocation == null) {
          message = "Changed trustfactor of " + player.getName() + " to " + factor.name() + " (unknown origin)";
        } else {
          message = "Changed trustfactor of " + player.getName() + " to " + factor.name() + " (plugin " + pluginInvocation.pluginName() + " in class " + pluginInvocation.className() + ")";
        }
        IntaveLogger.logger().info(message);
        user.setTrustFactor(factor);
      }

      @Override
      public PlayerClicks clicks() {
        return clickStatisticsAccessor.clickStatisticsOf(player);
      }

      @Override
      public PlayerConnection connection() {
        return netStatisticsAccessor.netStatisticsOf(player);
      }
    };
  }

  public PlayerNetStatisticsAccessor netStatisticsAccessor() {
    return netStatisticsAccessor;
  }

  public PlayerClickStatisticsAccessor clickStatisticsAccessor() {
    return clickStatisticsAccessor;
  }
}
