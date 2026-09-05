package de.jpx3.intave.executor.task;

import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.user.User;
import org.bukkit.World;

public final class Tasks {
  private static final boolean FOLIA_SERVER = detectFoliaServer();
  private static final TaskScheduler SCHEDULER = FOLIA_SERVER
    ? new FoliaTaskScheduler()
    : new BukkitTaskScheduler();

  private Tasks() {
  }

  public static Task delayed(Runnable runnable, long tickDelay) {
    return delayedNamed(null, runnable, tickDelay);
  }

  public static Task delayedNamed(String name, Runnable runnable, long tickDelay) {
    if (tickDelay < 0) {
      throw new IllegalArgumentException("Task delay cannot be negative");
    }
    return new DelayedTask(name, runnable, tickDelay);
  }

  public static Task periodic(Runnable runnable, long tickDelay, long tickPeriod) {
    return periodicNamed(null, runnable, tickDelay, tickPeriod);
  }

  public static Task periodicNamed(String name, Runnable runnable, long tickDelay, long tickPeriod) {
    if (tickDelay < 0) {
      throw new IllegalArgumentException("Task delay cannot be negative");
    }
    if (tickPeriod < 1) {
      throw new IllegalArgumentException("Task period must be positive");
    }
    return new PeriodicTask(name, runnable, tickDelay, tickPeriod);
  }

  public static void addShutdownHook() {
    ShutdownTasks.add(SCHEDULER::stopAll);
  }

  static void startSync(Task task) {
    SCHEDULER.startSync(task);
  }

  static void startUserSync(Task task, User user) {
    SCHEDULER.startUserSync(task, user);
  }

  static void startRegionSync(Task task, World world, int chunkX, int chunkZ) {
    SCHEDULER.startRegionSync(task, world, chunkX, chunkZ);
  }

  static void startAsync(Task task) {
    SCHEDULER.startAsync(task);
  }

  static void stop(Task task) {
    SCHEDULER.stop(task);
  }

  public static boolean isFoliaServer() {
    return FOLIA_SERVER;
  }

  private static boolean detectFoliaServer() {
    try {
      Class.forName(
        "io.papermc.paper.threadedregions.RegionizedServer",
        false,
        Tasks.class.getClassLoader()
      );
      return true;
    } catch (ClassNotFoundException ignored) {
      return false;
    }
  }
}
