package de.jpx3.intave.user.permission;

public interface PermissionCache {
  boolean cached(String permission);

  boolean check(String permission);

  void save(String permission, boolean access);
}
