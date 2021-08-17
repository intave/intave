package de.jpx3.intave.user.permission;

import de.jpx3.intave.tools.AccessHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ExpiringPermissionCache implements PermissionCache {
  private final long cacheExpiration;
  private final Map<String, ExpiringPermissionCacheEntry> permissionEntries = new ConcurrentHashMap<>();

  public ExpiringPermissionCache(long cacheExpires, TimeUnit unit) {
    cacheExpiration = unit.toMillis(cacheExpires);
  }

  @Override
  public boolean cached(String permission) {
    ExpiringPermissionCacheEntry entry = permissionEntries.get(permission);
    return entry != null && !entry.expired();
  }

  @Override
  public boolean check(String permission) {
    ExpiringPermissionCacheEntry entry = permissionEntries.get(permission);
    return entry != null && entry.hasAccess();
  }

  @Override
  public void save(String permission, boolean access) {
    permissionEntries.computeIfAbsent(permission, s -> new ExpiringPermissionCacheEntry(cacheExpiration)).setAccess(access);
  }

  public static class ExpiringPermissionCacheEntry {
    private boolean access;
    private long checked;
    private long duration;

    public ExpiringPermissionCacheEntry(long duration) {
      this.duration = duration;
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
      return AccessHelper.now() - checked > duration;
    }
  }
}
