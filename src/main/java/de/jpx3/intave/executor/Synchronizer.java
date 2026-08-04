package de.jpx3.intave.executor;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.Queue;
import java.util.concurrent.Executor;

public final class Synchronizer {
  // Resolved lazily: Bukkit has no server instance in the unit tests, and touching it
  // from a static initializer made every test that reaches this class fail with an
  // ExceptionInInitializerError instead of running.
  private static BukkitScheduler scheduler;
  private static final boolean isFolia = isFoliaServer();
  private static Executor globalSynchronizationExecutor;

  private static BukkitScheduler scheduler() {
    if (scheduler == null) {
      scheduler = Bukkit.getScheduler();
    }
    return scheduler;
  }

  /**
   * Whether this server runs on Folia's regionized threading (Folia, CanvasMC, …).
   * On such servers Bukkit entity/world handles may only be touched on the owning
   * region thread, so packet-thread code must resolve state without them.
   */
  public static boolean onFolia() {
    return isFolia;
  }

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
      globalSynchronizationExecutor = command -> scheduler().runTask(IntavePlugin.singletonInstance(), command);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Deprecated
  public static void synchronize(Runnable runnable) {
    if (isFolia) {
      Tasks.delayedNamed("Synchronizer.synchronize", wrapped(runnable), 0).startSync();
    } else {
      dispatchGlobally(runnable);
    }
  }

  public static void synchronize(User user, Runnable runnable) {
    if (isFolia) {
//      Thread.dumpStack();
      Tasks.delayedNamed(runnable.toString(), wrapped(runnable), 0).startUserSync(user);
    } else {
      dispatchGlobally(runnable);
    }
  }

  /**
   * Runs {@code runnable} on the thread owning {@code location}'s region, so it
   * may safely touch world/block state there. On non-Folia servers this is the
   * main thread. Prefer this over {@link #synchronize(Runnable)} for any task
   * that reads or writes the world at a known location.
   */
  public static void synchronize(Location location, Runnable runnable) {
    Tasks.delayedNamed("Synchronizer.synchronize@location", wrapped(runnable), 0).startRegionSync(location);
  }

  public static void synchronizeDelayed(Location location, Runnable runnable, int ticks) {
    Tasks.delayedNamed("Synchronizer.synchronizeDelayed@location", wrapped(runnable), ticks).startRegionSync(location);
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

  /**
   * Queues {@code runnable} onto the server's main thread. Before {@link #setup()}
   * has run -- unit tests, and the window before the plugin is enabled -- there is no
   * queue to hand it to, so the work runs inline: the caller's thread is the only one
   * there is at that point, and dropping the task silently would leave metadata
   * half-initialised.
   */
  private static void dispatchGlobally(Runnable runnable) {
    Runnable wrapped = wrapped(runnable);
    if (globalSynchronizationExecutor == null) {
      wrapped.run();
      return;
    }
    globalSynchronizationExecutor.execute(wrapped);
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