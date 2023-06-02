package de.jpx3.intave.cleanup;

import java.util.ArrayDeque;
import java.util.Deque;

public final class StartupTasks {
  private static final Deque<Runnable> tasks = new ArrayDeque<>();

  private static boolean done = false;

  private StartupTasks() {
    throw new UnsupportedOperationException("Initialization of helper class");
  }

  public static void add(Runnable runnable) {
    if (done) {
      throw new IllegalStateException("Startup tasks already ran");
    }
    if (runnable == null) {
      throw new NullPointerException("Null shutdown task");
    }
    tasks.offerLast(runnable);
  }

  public static void addBeforeAll(Runnable runnable) {
    if (done) {
      throw new IllegalStateException("Startup tasks already ran");
    }
    if (runnable == null) {
      throw new NullPointerException("Null shutdown task");
    }
    tasks.offerFirst(runnable);
  }

  public static void runAll() {
    for (Runnable task : tasks) {
      try {
        task.run();
      } catch (Exception exception) {
        System.out.println("[Intave] Shutdown task " + task + " failed to complete");
        exception.printStackTrace();
      }
    }
    done = true;
  }
}
