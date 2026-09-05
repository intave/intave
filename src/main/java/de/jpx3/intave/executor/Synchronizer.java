package de.jpx3.intave.executor;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.user.User;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.concurrent.Executor;

public final class Synchronizer {
  private static final BukkitScheduler scheduler = Bukkit.getScheduler();
  private static final Method IS_OWNED_BY_CURRENT_REGION = foliaApiMethod(
    "isOwnedByCurrentRegion",
    Entity.class
  );
  private static final Method IS_GLOBAL_TICK_THREAD = foliaApiMethod("isGlobalTickThread");
  private static Executor synchronizationExecutor;

  public static void setup() {
    if (Tasks.isFoliaServer()) {
      return;
    }
    try {
      Class<?> minecraftServerClass = Lookup.serverClass("MinecraftServer");
      Object minecraftServer = minecraftServerClass.getMethod("getServer").invoke(null);
      //noinspection unchecked
      Queue<Runnable> cachedProcessQueue = (Queue<Runnable>) minecraftServerClass.getField("processQueue").get(minecraftServer);
      synchronizationExecutor = cachedProcessQueue::add;
    } catch (NoSuchFieldException exception) {
      IntavePlugin.singletonInstance().logger().error("Your version of spigot has removed support for task-queueing. We will switch to bukkit's scheduling service");
      synchronizationExecutor = command -> scheduler.runTask(IntavePlugin.singletonInstance(), command);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Deprecated
  public static void synchronize(Runnable runnable) {
    Runnable wrapped = wrapped(runnable);
    if (Tasks.isFoliaServer()) {
      Tasks.delayedNamed("Synchronizer.synchronize", wrapped, 0).startSync();
    } else {
      synchronizationExecutor.execute(wrapped);
    }
  }

  public static void synchronize(User user, Runnable runnable) {
    if (Tasks.isFoliaServer()) {
      Tasks.delayedNamed("Synchronizer.synchronize", wrapped(runnable), 0).startUserSync(user);
    } else {
      synchronize(runnable);
    }
  }

  public static void synchronize(World world, int chunkX, int chunkZ, Runnable runnable) {
    Tasks.delayedNamed("Synchronizer.synchronize", wrapped(runnable), 0)
      .startRegionSync(world, chunkX, chunkZ);
  }

  @Deprecated
  public static void synchronizeDelayed(Runnable runnable, int ticks) {
    Tasks.delayedNamed("Synchronizer.synchronizeDelayed", wrapped(runnable), ticks).startSync();
  }

  public static void synchronizeDelayed(User user, Runnable runnable, int ticks) {
    Tasks.delayedNamed("Synchronizer.synchronizeDelayed", wrapped(runnable), ticks).startUserSync(user);
  }

  public static void synchronizeDelayed(World world, int chunkX, int chunkZ, Runnable runnable, int ticks) {
    Tasks.delayedNamed("Synchronizer.synchronizeDelayed", wrapped(runnable), ticks)
      .startRegionSync(world, chunkX, chunkZ);
  }

  public static boolean isSynchronized(User user) {
    if (!Tasks.isFoliaServer()) {
      return Bukkit.isPrimaryThread();
    }
    try {
      return (boolean) IS_OWNED_BY_CURRENT_REGION.invoke(null, user.player());
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof ClassCastException || cause instanceof UnsupportedOperationException) {
        return isGlobalTickThread();
      }
      throw new IllegalStateException("Unable to determine the player's owning region", exception);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Unable to determine the player's owning region", exception);
    }
  }

  private static boolean isGlobalTickThread() {
    try {
      return (boolean) IS_GLOBAL_TICK_THREAD.invoke(null);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Unable to determine the global region thread", exception);
    }
  }

  private static Runnable wrapped(Runnable runnable) {
    return () -> {
      try {
        Timings.EXE_SERVER.start();
        runnable.run();
      } catch (UnsupportedFallbackOperationException fallbackOp) {
        IntaveLogger.logger().info("Task " + runnable + " failed because the associated player logged off already");
      } catch (Exception | Error throwable) {
        IntaveLogger.logger().error("Failed to execute server task " + runnable);
        throwable.printStackTrace();
      } finally {
        Timings.EXE_SERVER.stop();
      }
    };
  }

  private static Method foliaApiMethod(String name, Class<?>... parameterTypes) {
    if (!Tasks.isFoliaServer()) {
      return null;
    }
    try {
      return Bukkit.class.getMethod(name, parameterTypes);
    } catch (NoSuchMethodException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
