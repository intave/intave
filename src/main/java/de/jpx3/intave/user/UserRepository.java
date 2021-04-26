package de.jpx3.intave.user;

import com.google.common.collect.Maps;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.event.punishment.EntityNoDamageTickChanger;
import de.jpx3.intave.tools.AccessHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class UserRepository {
  private final static Map<UUID, User> userRepository = Maps.newConcurrentMap();
  private final static User deadUser = User.empty();
  private final static Lock lock = new ReentrantLock();
  private static boolean closed;

  public static void registerUser(Player player) {
    userRepository.put(player.getUniqueId(), User.userFor(player));
    if (IntaveControl.RESET_HURT_TIME_ON_JOIN) {
      EntityNoDamageTickChanger.setNoDamageTicksOf(player, 20);
    }
  }

  public static boolean hasUser(Player player) {
    return userRepository.containsKey(player.getUniqueId());
  }

  public static void unregisterUser(Player player) {
    if (hasUser(player)) {
      User user = userOf(player);
      user.unregister();
    }
    userRepository.remove(player.getUniqueId());
  }

  public static User userOf(Player player) {
    User user = userRepository.get(player.getUniqueId());
    if(user == null) {
      if(closed) {
        return deadUser;
      }

      // check if player is offline
      boolean isOnline = AccessHelper.isOnline(player);
      // online -> recreate user object
      if(isOnline) {
        try {
//          lock.lock();
          registerUser(player);
          return userRepository.get(player.getUniqueId());
        } finally {
//          lock.unlock();
        }
      } else {
        // offline -> return dead user
        return deadUser;
      }
    }
    return user;
  }

  public static void die() {
    closed = true;
    unregisterAll();
    userRepository.clear();
  }

  private static void unregisterAll() {
    for (UUID uuid : userRepository.keySet()) {
      Player player = Bukkit.getPlayer(uuid);
      if (player != null) {
        unregisterUser(player);
      }
    }
  }
}