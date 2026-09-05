package de.jpx3.intave.executor.task;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.user.User;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

final class FoliaTaskScheduler implements TaskScheduler {
  private static final AsyncScheduler ASYNC_SCHEDULER;
  private static final GlobalRegionScheduler GLOBAL_SCHEDULER;
  private static final RegionScheduler REGION_SCHEDULER;
  private static final Function<Entity, EntityScheduler> ENTITY_SCHEDULER;

  static {
    try {
      Object server = Bukkit.getServer();
      GLOBAL_SCHEDULER = (GlobalRegionScheduler) server.getClass()
        .getMethod("getGlobalRegionScheduler").invoke(server);
      REGION_SCHEDULER = (RegionScheduler) server.getClass()
        .getMethod("getRegionScheduler").invoke(server);
      ASYNC_SCHEDULER = (AsyncScheduler) server.getClass()
        .getMethod("getAsyncScheduler").invoke(server);
      Method getScheduler = Entity.class.getMethod("getScheduler");
      ENTITY_SCHEDULER = entity -> {
        try {
          return (EntityScheduler) getScheduler.invoke(entity);
        } catch (InvocationTargetException exception) {
          if (exception.getCause() instanceof UnsupportedOperationException) {
            return null;
          }
          throw new IllegalStateException("Failed to get scheduler for entity " + entity.getUniqueId(), exception);
        } catch (ReflectiveOperationException exception) {
          throw new IllegalStateException("Failed to get scheduler for entity " + entity.getUniqueId(), exception);
        }
      };
    } catch (ReflectiveOperationException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private final Map<Task, ScheduledTask> scheduledTasks = new ConcurrentHashMap<>();

  @Override
  public void startSync(Task task) {
    ensureNotRunning(task);
    ScheduledTask scheduledTask;
    if (task.period() >= 1) {
      scheduledTask = GLOBAL_SCHEDULER.runAtFixedRate(
        IntavePlugin.singletonInstance(), ignored -> runTask(task), Math.max(1, task.delay()), task.period()
      );
    } else if (task.delay() == 0) {
      AtomicBoolean completed = new AtomicBoolean();
      scheduledTask = GLOBAL_SCHEDULER.run(
        IntavePlugin.singletonInstance(), ignored -> runOneShot(task, ignored, completed)
      );
      rememberOneShot(task, scheduledTask, completed);
      return;
    } else {
      AtomicBoolean completed = new AtomicBoolean();
      scheduledTask = GLOBAL_SCHEDULER.runDelayed(
        IntavePlugin.singletonInstance(), ignored -> runOneShot(task, ignored, completed), task.delay()
      );
      rememberOneShot(task, scheduledTask, completed);
      return;
    }
    if (scheduledTask != null) {
      scheduledTasks.put(task, scheduledTask);
    }
  }

  @Override
  public void startUserSync(Task task, User user) {
    ensureNotRunning(task);
    EntityScheduler scheduler = ENTITY_SCHEDULER.apply(user.player());
    if (scheduler == null) {
      startSync(task);
      return;
    }
    ScheduledTask scheduledTask;
    if (task.period() >= 1) {
      scheduledTask = scheduler.runAtFixedRate(
        IntavePlugin.singletonInstance(), ignored -> runTask(task), null, Math.max(1, task.delay()), task.period()
      );
    } else if (task.delay() == 0) {
      AtomicBoolean completed = new AtomicBoolean();
      scheduledTask = scheduler.run(
        IntavePlugin.singletonInstance(), ignored -> runOneShot(task, ignored, completed), null
      );
      rememberOneShot(task, scheduledTask, completed);
      return;
    } else {
      AtomicBoolean completed = new AtomicBoolean();
      scheduledTask = scheduler.runDelayed(
        IntavePlugin.singletonInstance(), ignored -> runOneShot(task, ignored, completed), null, task.delay()
      );
      rememberOneShot(task, scheduledTask, completed);
      return;
    }
    if (scheduledTask != null) {
      scheduledTasks.put(task, scheduledTask);
    }
  }

  @Override
  public void startRegionSync(Task task, World world, int chunkX, int chunkZ) {
    ensureNotRunning(task);
    ScheduledTask scheduledTask;
    if (task.period() >= 1) {
      scheduledTask = REGION_SCHEDULER.runAtFixedRate(
        IntavePlugin.singletonInstance(), world, chunkX, chunkZ,
        ignored -> runTask(task), Math.max(1, task.delay()), task.period()
      );
    } else if (task.delay() == 0) {
      AtomicBoolean completed = new AtomicBoolean();
      scheduledTask = REGION_SCHEDULER.run(
        IntavePlugin.singletonInstance(), world, chunkX, chunkZ,
        ignored -> runOneShot(task, ignored, completed)
      );
      rememberOneShot(task, scheduledTask, completed);
      return;
    } else {
      AtomicBoolean completed = new AtomicBoolean();
      scheduledTask = REGION_SCHEDULER.runDelayed(
        IntavePlugin.singletonInstance(), world, chunkX, chunkZ,
        ignored -> runOneShot(task, ignored, completed), task.delay()
      );
      rememberOneShot(task, scheduledTask, completed);
      return;
    }
    if (scheduledTask != null) {
      scheduledTasks.put(task, scheduledTask);
    }
  }

  @Override
  public void startAsync(Task task) {
    ensureNotRunning(task);
    ScheduledTask scheduledTask;
    if (task.period() >= 1) {
      scheduledTask = ASYNC_SCHEDULER.runAtFixedRate(
        IntavePlugin.singletonInstance(), ignored -> runTask(task),
        Math.max(1, task.delay()) * 50L, task.period() * 50L, TimeUnit.MILLISECONDS
      );
    } else if (task.delay() == 0) {
      AtomicBoolean completed = new AtomicBoolean();
      scheduledTask = ASYNC_SCHEDULER.runNow(
        IntavePlugin.singletonInstance(), ignored -> runOneShot(task, ignored, completed)
      );
      rememberOneShot(task, scheduledTask, completed);
      return;
    } else {
      AtomicBoolean completed = new AtomicBoolean();
      scheduledTask = ASYNC_SCHEDULER.runDelayed(
        IntavePlugin.singletonInstance(), ignored -> runOneShot(task, ignored, completed),
        task.delay() * 50L, TimeUnit.MILLISECONDS
      );
      rememberOneShot(task, scheduledTask, completed);
      return;
    }
    scheduledTasks.put(task, scheduledTask);
  }

  @Override
  public void stop(Task task) {
    ScheduledTask scheduledTask = scheduledTasks.remove(task);
    if (scheduledTask != null) {
      scheduledTask.cancel();
    }
  }

  @Override
  public void stopAll() {
    for (ScheduledTask task : scheduledTasks.values()) {
      task.cancel();
    }
    scheduledTasks.clear();
  }

  private void ensureNotRunning(Task task) {
    if (scheduledTasks.containsKey(task)) {
      throw new IllegalStateException("Task is already running: " + task.name());
    }
  }

  private void runOneShot(Task task, ScheduledTask scheduledTask, AtomicBoolean completed) {
    try {
      runTask(task);
    } finally {
      completed.set(true);
      scheduledTasks.remove(task, scheduledTask);
    }
  }

  private void runTask(Task task) {
    try {
      task.run();
    } catch (RuntimeException | Error throwable) {
      IntaveLogger.logger().error("Failed to execute scheduled task " + task.name());
      throwable.printStackTrace();
      throw throwable;
    }
  }

  private void rememberOneShot(Task task, ScheduledTask scheduledTask, AtomicBoolean completed) {
    if (scheduledTask == null) {
      return;
    }
    scheduledTasks.put(task, scheduledTask);
    if (completed.get()) {
      scheduledTasks.remove(task, scheduledTask);
    }
  }
}
