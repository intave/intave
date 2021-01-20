package de.jpx3.intave.user;

import com.google.common.collect.Maps;
import de.jpx3.intave.tools.AccessHelper;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public final class UserRepository {
  private final static Map<UUID, User> userRepository = Maps.newConcurrentMap();
  private final static User deadUser = User.empty();
  private final static Object lock = new Object();

  public static void registerUser(Player player) {
    userRepository.put(player.getUniqueId(), User.userFor(player));
  }

  public static boolean hasUser(Player player) {
    return userRepository.containsKey(player.getUniqueId());
  }

  public static void unregisterUser(Player player) {
    userRepository.remove(player.getUniqueId());
  }

  public static User userOf(Player player) {
    User user = userRepository.get(player.getUniqueId());
    if(user == null) {
      // check if player is offline
      boolean isOnline = AccessHelper.isOnline(player);
      // online -> recreate user object
      if(isOnline) {
        synchronized (lock) {
          registerUser(player);
          return userRepository.get(player.getUniqueId());
        }
      } else {
        // offline -> return dead user
        return deadUser;
      }
    }
    return user;
  }
}