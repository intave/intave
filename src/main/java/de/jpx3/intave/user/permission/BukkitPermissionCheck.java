package de.jpx3.intave.user.permission;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

public final class BukkitPermissionCheck {
  @Native
  public static boolean permissionCheck(Permissible permissible, String permission) {
    if (permissible instanceof Player) {
      if (permission.equalsIgnoreCase("sibyl") && IntavePlugin.singletonInstance().sibylIntegrationService().isAuthenticated((Player) permissible)) {
        return true;
      }
      return playerPermissionCheck((Player) permissible, permission);
    } else {
      // non-player can not inherit sibyl permissions, never
      if (permission.equalsIgnoreCase("sibyl")) {
        return false;
      }
      return nativePermissionCheck(permissible, permission);
    }
  }

  private static boolean playerPermissionCheck(Player player, String permission) {
    if (!UserRepository.hasUser(player)) {
      return nativePermissionCheck(player, permission);
    }
    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return false;
    }
    PermissionCache permissionCache = user.permissionCache();
    if (permissionCache.cached(permission)) {
      return permissionCache.check(permission);
    } else {
      boolean access = nativePermissionCheck(player, permission);
      permissionCache.save(permission, access);
      return access;
    }
  }

  private static boolean nativePermissionCheck(Permissible permissible, String permission) {
    return permissible.hasPermission(permission);
  }
}
