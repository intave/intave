package de.jpx3.intave.executor.task;

final class DelayedTask implements Task {
  private final String name;
  private final Runnable runnable;
  private final long delay;

  DelayedTask(String name, Runnable runnable, long delay) {
    this.name = name;
    this.runnable = runnable;
    this.delay = delay;
  }

  @Override
  public String name() {
    return String.format("delayed(%s, +%d)", name == null ? runnable : name, delay);
  }

  @Override
  public void run() {
    runnable.run();
  }

  @Override
  public long delay() {
    return delay;
  }
}
