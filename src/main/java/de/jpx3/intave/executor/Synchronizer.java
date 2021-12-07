package de.jpx3.intave.executor;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.klass.Lookup;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.InvocationTargetException;
import java.util.Queue;
import java.util.concurrent.Executor;

public final class Synchronizer {
  private final static BukkitScheduler scheduler = Bukkit.getScheduler();
  private static Executor synchronizationExecutor;

  public static void setup() {
    try {
      Class<?> minecraftServerClass = Lookup.serverClass("MinecraftServer");
      Object minecraftServer = minecraftServerClass.getMethod("getServer").invoke(null);
      //noinspection unchecked
      Queue<Runnable> cachedProcessQueue = (Queue<Runnable>) minecraftServerClass.getField("processQueue").get(minecraftServer);
      synchronizationExecutor = cachedProcessQueue::add;
    } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException exception) {
      throw new IllegalStateException(exception);
    } catch (NoSuchFieldException exception) {
      IntavePlugin.singletonInstance().logger().error("Your version of spigot has removed support for task-queueing. We will switch to bukkit's scheduling service");
      synchronizationExecutor = command -> scheduler.runTask(IntavePlugin.singletonInstance(), command);
    }
  }

  public static void synchronize(Runnable runnable) {
    runnable = wrapTask(runnable);
    synchronizationExecutor.execute(runnable);
  }

  public static void synchronizeDelayed(Runnable runnable, int ticks) {
    runnable = wrapTask(runnable);
    scheduler.runTaskLater(IntavePlugin.singletonInstance(), runnable, ticks);
  }

  private static Runnable wrapTask(Runnable runnable) {
    return () -> {
      try {
        Timings.EXE_SERVER.start();
        runnable.run();
      } catch (UnsupportedFallbackOperationException | IllegalArgumentException fallbackOp) {
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