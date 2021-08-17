package de.jpx3.intave.user.permission;

import de.jpx3.intave.tools.AccessHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class BukkitPermissionCache {
  private final static long CACHE_EXPIRE = TimeUnit.SECONDS.toMillis(16);
  private final Map<String, PermissionEntry> permissionEntries = new ConcurrentHashMap<>();

  public boolean inCache(String permission) {
    PermissionEntry entry;
    return (entry = permissionEntries.get(permission)) != null && !entry.expired();
  }

  public boolean permissionCheck(String permission) {
    PermissionEntry entry;
    return (entry = permissionEntries.get(permission)) != null && entry.hasAccess();
  }

  public void permissionSave(String permission, boolean access) {
    permissionEntries.computeIfAbsent(permission, s -> new PermissionEntry()).setAccess(access);
  }

  public static class PermissionEntry {
    private boolean access;
    private long checked;

    public PermissionEntry() {
    }

    public boolean hasAccess() {
      return access;
    }

    public void setAccess(boolean access) {
      this.access = access;
      this.checked = AccessHelper.now();
    }

    public long checked() {
      return checked;
    }

    public boolean expired() {
      return AccessHelper.now() - checked > CACHE_EXPIRE;
    }
  }
}
