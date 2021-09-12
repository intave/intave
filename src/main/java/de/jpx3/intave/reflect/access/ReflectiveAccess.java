package de.jpx3.intave.reflect.access;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.clazz.rewrite.PatchyLoadingInjector;

@Deprecated
public final class ReflectiveAccess {
  public static void setup() {
    ClassLoader classLoader = IntavePlugin.class.getClassLoader();
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.reflect.access.ReflectiveHandleAccess");
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.reflect.access.ReflectiveEntityAccess");
  }
}