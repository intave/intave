package de.jpx3.intave.executor;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.diagnostics.timings.Timings;
import de.jpx3.intave.reflect.Lookup;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Queue;

public final class Synchronizer {
  private final static BukkitScheduler scheduler = Bukkit.getScheduler();

  private static Queue<Runnable> cachedProcessQueue;
  private static boolean useScheduler;
  private static Object minecraftServer;
  private static MethodHandle postToMainThreadMethodHandle;

  public static void setup() {
    try {
      Class<?> minecraftServerClass = Lookup.serverClass("MinecraftServer");
      Object minecraftServer = minecraftServerClass.getMethod("getServer").invoke(null);
      //noinspection unchecked
      cachedProcessQueue = (Queue<Runnable>) minecraftServerClass.getField("processQueue").get(minecraftServer);
    } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException exception) {
      throw new IllegalStateException(exception);
    } catch (NoSuchFieldException exception) {
      IntavePlugin.singletonInstance().logger().error("Your version of spigot has removed support for task-queueing. We will switch to bukkits scheduling service");
      useScheduler = true;
    }

    try {
      minecraftServer = Lookup.serverClass("MinecraftServer").getMethod("getServer").invoke(null);
      Class<?> serverClass = minecraftServer.getClass();
      while ((serverClass = serverClass.getSuperclass()) != Object.class) {
        Method[] declaredMethods = serverClass.getDeclaredMethods();
        if (Arrays.stream(declaredMethods).anyMatch(method -> method.getName().equals("postToMainThread"))) {
          break;
        }
      }
      Method postToMainThreadMethod = serverClass.getDeclaredMethod("postToMainThread", Runnable.class);
      if (!postToMainThreadMethod.isAccessible()) {
        postToMainThreadMethod.setAccessible(true);
      }
      postToMainThreadMethodHandle = MethodHandles.lookup().unreflect(postToMainThreadMethod);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static void synchronize(Runnable runnable) {
    runnable = wrapTask(runnable);
    if (useScheduler) {
      scheduler.runTask(IntavePlugin.singletonInstance(), runnable);
    } else {
      cachedProcessQueue.add(runnable);
    }
  }

  @Deprecated
  public static void packetSynchronize(Runnable runnable) {
    runnable = wrapTask(runnable);
    try {
      postToMainThreadMethodHandle.invoke(minecraftServer, runnable);
    } catch (Throwable throwable) {
      throwable.printStackTrace();
    }
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