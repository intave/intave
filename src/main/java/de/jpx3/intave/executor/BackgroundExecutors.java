package de.jpx3.intave.executor;

import com.google.common.collect.Maps;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.diagnostic.timings.Timing;
import de.jpx3.intave.diagnostic.timings.Timings;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.executor.BackgroundExecutors.ExecutorType.*;

public final class BackgroundExecutors {
  private static Map<ExecutorType, ExecutorService> executorServices = Maps.newEnumMap(ExecutorType.class);

  public static void start() {
    IntaveThreadFactory lpFactory = IntaveThreadFactory.ofLowestPriority();
    executorServices.put(IN_TIME, Executors.newSingleThreadExecutor(lpFactory));
    executorServices.put(SCHEDULED, Executors.newSingleThreadScheduledExecutor(lpFactory));
    executorServices.put(NO_TIME_REQUIREMENT, Executors.newSingleThreadExecutor(lpFactory));
    executorServices.put(NO_TIME_AND_EXECUTION_REQUIREMENT, executorServices.get(NO_TIME_REQUIREMENT));
  }

  public static void execute(Runnable runnable) {
    execute(runnable, IN_TIME);
  }

  public static void executeWhenever(Runnable runnable) {
    execute(runnable, NO_TIME_REQUIREMENT);
  }

  public static void tryExecuteWhenever(Runnable runnable) {
    execute(runnable, NO_TIME_AND_EXECUTION_REQUIREMENT);
  }

  public static void executeExternallyScheduled(Runnable runnable) {
    execute(runnable, SCHEDULED);
  }

  private static void execute(Runnable runnable, ExecutorType type) {
    ExecutorService service = executorServices.get(type);
    if (service == null || service.isShutdown() || service.isTerminated()) {
      return;
    }
    service.execute(wrapTask(runnable, type));
  }

  private static Runnable wrapTask(Runnable runnable, ExecutorType type) {
    String typeName = "\"" + type.name().toLowerCase().replace("_", "-") + "\"";
    ExecutorService service = executorServices.get(type);
    Timing timings = type.timing();
    boolean dropAllowed = type.allowDrop();

    Reference<Runnable> softReference;
    if (dropAllowed) {
      softReference = new SoftReference<>(runnable);
    } else {
      softReference = null;
    }
    Callable<Runnable> runnableAccess = () -> {
      if (dropAllowed) {
        return softReference.get();
      } else {
        return runnable;
      }
    };

    return () -> {
      try {
        timings.start();
        Runnable task = runnableAccess.call();
        if (task != null) {
          task.run();
        }
      } catch (Exception | Error throwable) {
        if (service.isShutdown() || service.isTerminated()) {
          return;
        }
        IntaveLogger.logger().error("Failed to execute background task " + runnable + " in " + typeName + " executor");
        throwable.printStackTrace();
      } finally {
        timings.stop();
      }
    };
  }

  public static void stopAllBlocking() {
    for (ExecutorType value : ExecutorType.values()) {
      stopBlocking(value);
    }
  }

  private static void stopBlocking(ExecutorType type) {
    String typeName = "\"" + type.name().toLowerCase().replace("_", "-") + "\"";
    ExecutorService service = executorServices.get(type);
    if (service == null) {
      return;
    }
    if (type.allowDrop()) {
      service.shutdownNow();
      return;
    }
    List<Runnable> tasks = service.shutdownNow();
    if (!tasks.isEmpty()) {
      IntavePlugin.singletonInstance().logger().info("Waiting for "+typeName+" background tasks to complete");
    }
    for (Runnable runnable : tasks) {
      runnable.run();
    }
    try {
      if (!service.awaitTermination(16, TimeUnit.SECONDS)) {
        IntavePlugin.singletonInstance().logger().info("Unable to complete "+typeName+" background tasks after 16s");
      }
    } catch (InterruptedException exception) {
      exception.printStackTrace();
    }
  }

  @KeepEnumInternalNames
  public enum ExecutorType {
    IN_TIME(Timings.EXE_BACKGROUND_PRIMARY, false, TimeUnit.MILLISECONDS),
    SCHEDULED(Timings.EXE_BACKGROUND_TERTIARY, false, TimeUnit.SECONDS),
    NO_TIME_REQUIREMENT(Timings.EXE_BACKGROUND_SECONDARY, false, TimeUnit.DAYS),
    NO_TIME_AND_EXECUTION_REQUIREMENT(Timings.EXE_BACKGROUND_SECONDARY, true, TimeUnit.DAYS)
    ;

    final Timing timing;
    final boolean allowDrop;
    final TimeUnit suggestedExecutionScale;

    ExecutorType(Timing timing, boolean allowDrop, TimeUnit suggestedExecutionScale) {
      this.timing = timing;
      this.allowDrop = allowDrop;
      this.suggestedExecutionScale = suggestedExecutionScale;
    }

    public Timing timing() {
      return timing;
    }

    public boolean allowDrop() {
      return allowDrop;
    }

    public TimeUnit suggestedExecutionScale() {
      return suggestedExecutionScale;
    }
  }
}
