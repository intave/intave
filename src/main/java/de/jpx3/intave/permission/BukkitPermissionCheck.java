package de.jpx3.intave.permission;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.tools.annotate.Native;
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
    BukkitPermissionCache permissionCache = user.permissionCache();
    if (permissionCache.inCache(permission)) {
      return permissionCache.permissionCheck(permission);
    } else {
      boolean access = nativePermissionCheck(player, permission);
      permissionCache.permissionSave(permission, access);
      return access;
    }
  }

  private static boolean nativePermissionCheck(Permissible permissible, String permission) {
    return permissible.hasPermission(permission);
  }
}
