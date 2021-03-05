package de.jpx3.intave.accessbackend.player;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.*;
import de.jpx3.intave.tools.GarbageCollector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PlayerAccessor {
  private final IntavePlugin plugin;
  private final Map<UUID, PlayerAccess> playerAccessCache = GarbageCollector.watch(Maps.newConcurrentMap());
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

  private final static Map<String, Double> DEFAULT_RETURN = new HashMap<>();

  private PlayerAccess newPlayerAccess(Player player) {
    User user = UserRepository.userOf(player);
    Map<String, Map<String, Double>> violationLevel = user.meta().violationLevelData().violationLevel;

    return new PlayerAccess() {
      @Override
      public int protocolVersion() {
        return user.meta().clientData().protocolVersion();
      }

      @Override
      public double violationLevel(String check, String threshold) {
        check = check.toLowerCase(Locale.ROOT);
        if (!plugin.checkService().hasCheck(check)) {
          throw new UnknownCheckException("Unable to locale check \"" + check + "\"");
        }
        return violationLevel.getOrDefault(check, DEFAULT_RETURN).getOrDefault(threshold, 0d);
      }

      @Override
      public void addViolationPoints(String check, String threshold, double amount) {
        check = check.toLowerCase(Locale.ROOT);
        if (!plugin.checkService().hasCheck(check)) {
          throw new UnknownCheckException("Unable to locale check \"" + check + "\"");
        }
        violationLevel.getOrDefault(check, DEFAULT_RETURN).put(threshold, violationLevel(check, threshold) + amount);
      }

      @Override
      public void resetViolationLevel(String check, String threshold) {
        check = check.toLowerCase(Locale.ROOT);
        if (!plugin.checkService().hasCheck(check)) {
          throw new UnknownCheckException("Unable to locale check \"" + check + "\"");
        }
        violationLevel.getOrDefault(check, DEFAULT_RETURN).remove(threshold);
      }

      @Override
      public TrustFactor trustFactor() {
        return user.trustFactor();
      }

      @Override
      public PlayerNetStatistics connection() {
        return netStatisticsAccessor.netStatisticsOf(player);
      }

      @Override
      public PlayerClickStatistics clicks() {
        return clickStatisticsAccessor.clickStatisticsOf(player);
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
