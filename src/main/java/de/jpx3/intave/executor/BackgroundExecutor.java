package de.jpx3.intave.executor;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.diagnostic.timings.Timings;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class BackgroundExecutor {
  private static ExecutorService executor;

  public static void start() {
    executor = Executors.newSingleThreadExecutor(IntaveThreadFactory.ofLowestPriority());
  }

  public static void execute(Runnable runnable) {
    if (executor == null || executor.isShutdown() || executor.isTerminated()) {
      return;
    }
    executor.execute(wrapTask(runnable));
  }

  private static Runnable wrapTask(Runnable runnable) {
    return () -> {
      try {
        Timings.EXE_BACKGROUND.start();
        runnable.run();
      } catch (Exception | Error throwable) {
        if (executor.isShutdown() || executor.isTerminated()) {
          return;
        }
        IntaveLogger.logger().error("Failed to execute background task " + runnable);
        throwable.printStackTrace();
      } finally {
        Timings.EXE_BACKGROUND.stop();
      }
    };
  }

  public static void stopBlocking() {
    if (executor == null) {
      return;
    }
    List<Runnable> tasks = executor.shutdownNow();
    if (!tasks.isEmpty()) {
      IntavePlugin.singletonInstance().logger().info("Waiting for background tasks to complete");
    }
    for (Runnable runnable : tasks) {
      runnable.run();
    }
    try {
      if (!executor.awaitTermination(16, TimeUnit.SECONDS)) {
        IntavePlugin.singletonInstance().logger().info("Unable to complete background tasks after 16s");
      }
    } catch (InterruptedException exception) {
      exception.printStackTrace();
    }
  }
}
