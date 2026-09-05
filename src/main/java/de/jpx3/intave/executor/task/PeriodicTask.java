package de.jpx3.intave.executor.task;

final class PeriodicTask implements Task {
  private final String name;
  private final Runnable runnable;
  private final long delay;
  private final long period;

  PeriodicTask(String name, Runnable runnable, long delay, long period) {
    this.name = name;
    this.runnable = runnable;
    this.delay = delay;
    this.period = period;
  }

  @Override
  public String name() {
    return String.format("periodic(%s, +%d, @%d)", name == null ? runnable : name, delay, period);
  }

  @Override
  public void run() {
    runnable.run();
  }

  @Override
  public long delay() {
    return delay;
  }

  @Override
  public long period() {
    return period;
  }
}
