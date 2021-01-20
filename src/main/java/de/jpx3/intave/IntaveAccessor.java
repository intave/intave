package de.jpx3.intave;

import de.jpx3.intave.access.IntaveAccess;
import de.jpx3.intave.access.IntaveColdException;
import de.jpx3.intave.tools.annotate.Native;

import java.lang.ref.WeakReference;

public final class IntaveAccessor {
  private static transient WeakReference<IntaveAccess> weakAccess;

  @Native
  public static synchronized boolean loaded() {
    IntavePlugin plugin = IntavePlugin.singletonInstance();
    return plugin != null && plugin.isEnabled() && uncheckedUnsafeAccess() != null;
  }

  @Native
  public static synchronized WeakReference<IntaveAccess> weakAccess() {
    if (!loaded()) {
      throw new IntaveColdException("Intave offline");
    }
    if(weakAccess == null) {
      weakAccess = new WeakReference<>(uncheckedUnsafeAccess());
    }
    return weakAccess;
  }

  @Native
  public static synchronized IntaveAccess unsafeAccess() {
    if (!loaded()) {
      throw new IntaveColdException("Intave offline");
    }
    return uncheckedUnsafeAccess();
  }

  @Native
  private static IntaveAccess uncheckedUnsafeAccess() {
    return null;//IntavePlugin.singletonInstance().intaveAccess();
  }
}
