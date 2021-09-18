package de.jpx3.intave.user;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.diagnostic.MemoryWatchdog;
import de.jpx3.intave.module.mitigate.HurtimeModifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UserRepository {
  private final static Map<UUID, User> repository = MemoryWatchdog.watch("users", new ConcurrentHashMap<>());
  private final static User fallbackUser = UserFactory.createFallback();
  private static boolean closed;

  // used to load the class on startup
  public static void setup() {
    ShutdownTasks.add(UserRepository::die);
  }

  public static void registerUser(Player player) {
    repository.put(player.getUniqueId(), UserFactory.createUserFor(player));
    if (IntaveControl.RESET_HURT_TIME_ON_JOIN) {
      HurtimeModifier.setNoDamageTicksOf(player, 20);
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
    return user != null ? user : fallbackUser;
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