package de.jpx3.intave.executor;

import de.jpx3.intave.IntavePlugin;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BackgroundExecutor {
  private static ExecutorService executorService;

  public static void start() {
    executorService = Executors.newSingleThreadExecutor(IntaveThreadFactory.ofPriority(Thread.MIN_PRIORITY));
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
    executorService.execute(runnable);
  }
}
