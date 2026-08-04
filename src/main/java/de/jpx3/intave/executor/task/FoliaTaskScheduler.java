package de.jpx3.intave.executor.task;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.user.User;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

// This class is only loaded when we have folia installed,
// which means we can directly bind to the classes without worries
final class FoliaTaskScheduler implements TaskScheduler {
	private final static AsyncScheduler asyncScheduler;
	private final static GlobalRegionScheduler globalScheduler;
	private final static RegionScheduler regionScheduler;
	private final static Function<Entity, EntityScheduler> schedulerFromEntity;

	static {
		try {
			globalScheduler = (GlobalRegionScheduler) Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
			asyncScheduler = (AsyncScheduler) Bukkit.getServer().getClass().getMethod("getAsyncScheduler").invoke(Bukkit.getServer());
			regionScheduler = (RegionScheduler) Bukkit.getServer().getClass().getMethod("getRegionScheduler").invoke(Bukkit.getServer());
			Method getScheduler = Entity.class.getMethod("getScheduler");
			schedulerFromEntity = entity -> {
				try {
					return (EntityScheduler) getScheduler.invoke(entity);
				} catch (Throwable t) {
					t.printStackTrace();
					throw new RuntimeException("Failed to get scheduler from entity " + entity.getUniqueId(), t);
				}
			};
		} catch (Throwable t) {
			t.printStackTrace();
			throw new RuntimeException("Failed to initialize FoliaTaskScheduler", t);
		}
	}

	private final Map<Task, ScheduledTask> taskMap = new ConcurrentHashMap<>();

	private static boolean pluginDisabled() {
		// During shutdown a still-running async task (e.g. PlayerTime's transaction
		// loop) can try to schedule region/entity work; Folia throws
		// IllegalPluginAccessException once the plugin is disabled. Skip scheduling
		// rather than let that exception spam the log.
		return !IntavePlugin.singletonInstance().isEnabled();
	}

	@Override
	public void startSync(Task task) {
//		System.out.println("Starting task " + task.name() + " synchronously");
		if (pluginDisabled()) {
			return;
		}
		ScheduledTask outTask;
		if (task.period() >= 1) {
			outTask = globalScheduler.runAtFixedRate(
				IntavePlugin.singletonInstance(),
				scheduledTask -> task.run(),
				task.delay(), task.period()
			);
		} else if (task.delay() == 0) {
			outTask = globalScheduler.run(
				IntavePlugin.singletonInstance(),
				scheduledTask -> task.run()
			);
		} else {
			outTask = globalScheduler.runDelayed(
				IntavePlugin.singletonInstance(),
				scheduledTask -> task.run(),
				task.delay()
			);
		}
		taskMap.put(task, outTask);
	}

	@Override
	public void startUserSync(Task task, User user) {
//		System.out.println("Starting task " + task.name() + " user-synchronously");
		if (pluginDisabled()) {
			return;
		}
		if (user.synthetic()) {
			// Test/fallback users have no region-owned entity and therefore no
			// EntityScheduler; run region-independently on the global scheduler.
			startSync(task);
			return;
		}
		EntityScheduler scheduler = schedulerFromEntity.apply(user.player());
		ScheduledTask outTask;
		if (task.period() >= 1) {
			outTask = scheduler.runAtFixedRate(
				IntavePlugin.singletonInstance(),
				scheduledTask -> task.run(),
				null, task.delay(), task.period()
			);
		} else if (task.delay() == 0) {
			outTask = scheduler.run(
				IntavePlugin.singletonInstance(),
				scheduledTask -> task.run(),
				null
			);
		} else {
			outTask = scheduler.runDelayed(
				IntavePlugin.singletonInstance(),
				scheduledTask -> task.run(),
				null, task.delay()
			);
		}
		taskMap.put(task, outTask);
	}

	@Override
	public void startRegionSync(Task task, Location location) {
		if (pluginDisabled()) {
			return;
		}
		ScheduledTask outTask;
		if (task.period() >= 1) {
			outTask = regionScheduler.runAtFixedRate(
				IntavePlugin.singletonInstance(),
				location,
				scheduledTask -> task.run(),
				Math.max(1L, task.delay()), task.period()
			);
		} else if (task.delay() <= 0) {
			outTask = regionScheduler.run(
				IntavePlugin.singletonInstance(),
				location,
				scheduledTask -> task.run()
			);
		} else {
			outTask = regionScheduler.runDelayed(
				IntavePlugin.singletonInstance(),
				location,
				scheduledTask -> task.run(),
				task.delay()
			);
		}
		taskMap.put(task, outTask);
	}

	@Override
	public void startAsync(Task task) {
//		System.out.println("Starting task " + task.name() + " asynchronously");
		if (pluginDisabled()) {
			return;
		}
		ScheduledTask outTask;
		if (task.period() >= 1) {
			outTask = asyncScheduler.runAtFixedRate(
				IntavePlugin.singletonInstance(),
				scheduledTask -> task.run(),
				task.delay() * 50L, task.period() * 50L, TimeUnit.MILLISECONDS
			);
		} else {
			outTask = asyncScheduler.runDelayed(
				IntavePlugin.singletonInstance(),
				scheduledTask -> task.run(),
				task.delay() * 50L, TimeUnit.MILLISECONDS
			);
		}
		taskMap.put(task, outTask);
	}

	@Override
	public void stop(Task task) {
		System.out.println("Stopping task " + task.name());
		ScheduledTask outTask = taskMap.remove(task);
		if (outTask != null) {
			outTask.cancel();
		}
	}

	@Override
	public void stopAll() {
		System.out.println("Stopping all tasks");
		for (ScheduledTask outTask : taskMap.values()) {
			outTask.cancel();
		}
		taskMap.clear();
	}
}
