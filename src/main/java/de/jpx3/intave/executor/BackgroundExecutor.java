package de.jpx3.intave.executor;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.diagnostics.timings.Timings;
import de.jpx3.intave.logging.IntaveLogger;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BackgroundExecutor {
  private static ExecutorService executorService;

  public static void start() {
    executorService = Executors.newSingleThreadExecutor(IntaveThreadFactory.ofLowestPriority());
  }

  public static void stopBlocking() {
    if(executorService == null) {
      return;
    }
    List<Runnable> runnables = executorService.shutdownNow();
    if(!runnables.isEmpty()) {
      IntavePlugin.singletonInstance().logger().info("Waiting for background tasks to finish");
    }
    for (Runnable runnable : runnables) {
      runnable.run();
    }
  }

  public static void execute(Runnable runnable) {
    if(executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
      return;
    }
    runnable = wrapTask(runnable);
    executorService.execute(runnable);
  }

  private static Runnable wrapTask(Runnable runnable) {
    return () -> {
      try {
        Timings.EXE_BACKGROUND.start();
        runnable.run();
      } catch (Exception | Error throwable) {
        IntaveLogger.logger().error("Failed to execute background task " + runnable);
        throwable.printStackTrace();
      } finally {
        Timings.EXE_BACKGROUND.stop();
      }
    };
  }
}
