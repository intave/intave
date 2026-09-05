package de.jpx3.intave.executor.task;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.user.User;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class BukkitTaskScheduler implements TaskScheduler {
  private final Map<Task, Integer> taskIds = new ConcurrentHashMap<>();

  @Override
  public void startSync(Task task) {
    ensureNotRunning(task);
    int taskId;
    if (task.period() >= 1) {
      taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
        IntavePlugin.singletonInstance(), task::run, task.delay(), task.period()
      );
    } else {
      AtomicBoolean completed = new AtomicBoolean();
      taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(
        IntavePlugin.singletonInstance(), oneShot(task, completed), task.delay()
      );
      taskIds.put(task, taskId);
      if (completed.get()) {
        taskIds.remove(task, taskId);
      }
      return;
    }
    taskIds.put(task, taskId);
  }

  @Override
  public void startUserSync(Task task, User user) {
    startSync(task);
  }

  @Override
  public void startRegionSync(Task task, World world, int chunkX, int chunkZ) {
    startSync(task);
  }

  @Override
  public void startAsync(Task task) {
    ensureNotRunning(task);
    int taskId;
    if (task.period() >= 1) {
      taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
        IntavePlugin.singletonInstance(), task::run, task.delay(), task.period()
      ).getTaskId();
    } else {
      AtomicBoolean completed = new AtomicBoolean();
      taskId = Bukkit.getScheduler().runTaskLaterAsynchronously(
        IntavePlugin.singletonInstance(), oneShot(task, completed), task.delay()
      ).getTaskId();
      taskIds.put(task, taskId);
      if (completed.get()) {
        taskIds.remove(task, taskId);
      }
      return;
    }
    taskIds.put(task, taskId);
  }

  @Override
  public void stop(Task task) {
    Integer taskId = taskIds.remove(task);
    if (taskId != null) {
      Bukkit.getScheduler().cancelTask(taskId);
    }
  }

  @Override
  public void stopAll() {
    for (Integer taskId : taskIds.values()) {
      Bukkit.getScheduler().cancelTask(taskId);
    }
    taskIds.clear();
  }

  private void ensureNotRunning(Task task) {
    if (taskIds.containsKey(task)) {
      throw new IllegalStateException("Task is already running: " + task.name());
    }
  }

  private Runnable oneShot(Task task, AtomicBoolean completed) {
    return () -> {
      try {
        task.run();
      } finally {
        completed.set(true);
        taskIds.remove(task);
      }
    };
  }
}
