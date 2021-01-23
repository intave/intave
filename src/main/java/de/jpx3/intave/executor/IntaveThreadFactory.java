package de.jpx3.intave.executor;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class IntaveThreadFactory implements ThreadFactory {
  private final static AtomicInteger poolNumber = new AtomicInteger(1);
  private final static ThreadGroup intaveThreadGroup = new ThreadGroup("intave");
  private final AtomicInteger threadNumber = new AtomicInteger(1);

  private final int currentPoolNumber;
  private final int defaultPriority;

  private IntaveThreadFactory(int defaultPriority) {
    this.currentPoolNumber = poolNumber.getAndIncrement();
    this.defaultPriority = defaultPriority;
  }

  public Thread newThread(Runnable runnable) {
    Thread thread = new Thread(intaveThreadGroup, wrapTask(runnable), newThreadName(), 0);
    thread.setDaemon(false);
    thread.setPriority(defaultPriority);
    thread.setUncaughtExceptionHandler((threadx, exception) -> {
      System.out.println("Thread " + threadx.getName() + " has encountered an " + exception);
      exception.printStackTrace();
    });
    return thread;
  }

  private String newThreadName() {
    return String.format("Intave (%02X-%02X)", currentPoolNumber, Math.min(threadNumber.getAndIncrement(), 99));
  }

  private Runnable wrapTask(Runnable runnable) {
//    IntavePerformanceTelemetry performanceTelemetry = IntavePlugin.staticReference().reporterService().performanceTelemetry();
    return () -> {
//      performanceTelemetry.processWorkerThreadActivation(Thread.currentThread());
      runnable.run();
//      performanceTelemetry.processWorkerThreadDeactivation(Thread.currentThread());
    };
  }

  public static IntaveThreadFactory ofHighestPriority() {
    return ofPriority(Thread.MAX_PRIORITY);
  }

  public static IntaveThreadFactory ofLowestPriority() {
    return ofPriority(Thread.MIN_PRIORITY);
  }

  public static IntaveThreadFactory ofPriority(int threadPriority) {
    return new IntaveThreadFactory(threadPriority);
  }
}