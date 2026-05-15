package de.jpx3.intave.executor;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.user.User;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.Queue;
import java.util.concurrent.Executor;

public final class Synchronizer {
  private static final BukkitScheduler scheduler = Bukkit.getScheduler();
  private static final boolean isFolia = isFoliaServer();
  private static Executor globalSynchronizationExecutor;

  public static void setup() {
    if (isFolia) {
      return;
    }
    try {
      Class<?> minecraftServerClass = Lookup.serverClass("MinecraftServer");
      Object minecraftServer = minecraftServerClass.getMethod("getServer").invoke(null);
      //noinspection unchecked
      Queue<Runnable> cachedProcessQueue = (Queue<Runnable>) minecraftServerClass.getField("processQueue").get(minecraftServer);
      globalSynchronizationExecutor = cachedProcessQueue::add;
    } catch (NoSuchFieldException exception) {
      IntavePlugin.singletonInstance().logger().error("Your version of spigot has removed support for task-queueing. We will switch to bukkit's scheduling service");
      globalSynchronizationExecutor = command -> scheduler.runTask(IntavePlugin.singletonInstance(), command);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Deprecated
  public static void synchronize(Runnable runnable) {
    if (isFolia) {
      Tasks.delayedNamed("Synchronizer.synchronize", wrapped(runnable), 0).startSync();
    } else {
      globalSynchronizationExecutor.execute(wrapped(runnable));
    }
  }

  public static void synchronize(User user, Runnable runnable) {
    if (isFolia) {
//      Thread.dumpStack();
      Tasks.delayedNamed(runnable.toString(), wrapped(runnable), 0).startUserSync(user);
    } else {
      globalSynchronizationExecutor.execute(wrapped(runnable));
    }
  }

  @Deprecated
  public static void synchronizeDelayed(Runnable runnable, int ticks) {
    runnable = wrapped(runnable);
    Tasks.delayed(runnable, ticks).startSync();
  }

  public static void synchronizeDelayed(User user, Runnable runnable, int ticks) {
    runnable = wrapped(runnable);
    Tasks.delayed(runnable, ticks).startUserSync(user);
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

  private static boolean isFoliaServer() {
    // This is the officially correct way to check for Folia!
    // https://docs.papermc.io/paper/dev/folia-support/
    try {
      Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}