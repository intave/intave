package de.jpx3.intave.executor;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.diagnostic.timings.Timings;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.concurrent.Executor;

public final class Synchronizer {
  private static final BukkitScheduler scheduler = Bukkit.getScheduler();
  private static Executor synchronizationExecutor;

  public static void setup() {
    synchronizationExecutor = command -> scheduler.runTask(IntavePlugin.singletonInstance(), command);
  }

  public static void synchronize(Runnable runnable) {
    if (synchronizationExecutor == null) {
      setup();
    }
    synchronizationExecutor.execute(wrapped(runnable));
  }

  public static void synchronizeDelayed(Runnable runnable, int ticks) {
    runnable = wrapped(runnable);
    scheduler.runTaskLater(IntavePlugin.singletonInstance(), runnable, ticks);
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
}
