package de.jpx3.intave.user;

import com.google.common.collect.Maps;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.event.violation.EntityNoDamageTickChanger;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.MemoryWatchdog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public final class UserRepository {
  private final static Map<UUID, User> repository = MemoryWatchdog.watch("users", Maps.newConcurrentMap());
  private final static User fallbackUser = UserFactory.newFallback();
  private static boolean closed;

  // used to load the class on startup
  public static void setup() {

  }

  public static void registerUser(Player player) {
    repository.put(player.getUniqueId(), UserFactory.newFor(player));
    if (IntaveControl.RESET_HURT_TIME_ON_JOIN) {
      EntityNoDamageTickChanger.setNoDamageTicksOf(player, 20);
    }
  }

  public static boolean hasUser(Player player) {
    return repository.containsKey(player.getUniqueId());
  }

  public static void unregisterUser(Player player) {
    if (hasUser(player)) {
      User user = userOf(player);
      user.unregister();
    }
    repository.remove(player.getUniqueId());
  }

  public static User userOf(Player player) {
    if (player == null) {
      return fallbackUser;
    }
    User user = repository.get(player.getUniqueId());
    if (user == null) {
      if (closed) {
        return fallbackUser;
      }
      // check if player is offline
      boolean isOnline = AccessHelper.isOnline(player);
      // online -> recreate user object
      if (isOnline) {
        registerUser(player);
        return repository.get(player.getUniqueId());
      } else {
        // offline -> return dead user
        return fallbackUser;
      }
    }
    return user;
  }

  public static void die() {
    closed = true;
    unregisterAll();
    repository.clear();
  }

  private static void unregisterAll() {
    for (UUID uuid : repository.keySet()) {
      Player player = Bukkit.getPlayer(uuid);
      if (player != null) {
        unregisterUser(player);
      }
    }
  }
}