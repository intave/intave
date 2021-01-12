package de.jpx3.intave.permission;

import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

public final class PermissionCheck {
  public static boolean permissionCheck(Permissible permissible, String permission) {
    if(permissible instanceof Player) {
      return playerPermissionCheck((Player) permissible, permission);
    } else {
      return permissible.hasPermission(permission);
    }
  }

  private static boolean playerPermissionCheck(Player player, String permission) {
    if(!UserRepository.hasUser(player)) {
      return false;
    }
    PermissionCache permissionCache = UserRepository.userOf(player).permissionCache();
    if(permissionCache.inCache(permission)) {
      return permissionCache.permissionCheck(permission);
    } else {
      boolean access = player.hasPermission(permission);
      permissionCache.permissionSave(permission, access);
      return access;
    }
  }
}
