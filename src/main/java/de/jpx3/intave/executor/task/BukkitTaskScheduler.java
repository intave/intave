package de.jpx3.intave.executor.task;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class BukkitTaskScheduler implements TaskScheduler {
	private static final Map<Task, Integer> taskToId = new ConcurrentHashMap<>();

	@Override
	public void startSync(Task task) {
		if (taskToId.containsKey(task)) {
			throw new IllegalStateException("Task is already running");
		}

		int taskId;
		if (task.period() >= 1) {
			taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
				IntavePlugin.singletonInstance(),
				task::run,
				task.delay(), task.period()
			);
		} else {
			taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(
				IntavePlugin.singletonInstance(),
				task::run,
				task.delay()
			);
		}
		taskToId.put(task, taskId);
	}

	@Override
	public void startUserSync(Task task, User user) {
		startSync(task);
	}

	@Override
	public void startRegionSync(Task task, Location location) {
		// On non-Folia servers all regions share the single main thread.
		startSync(task);
	}

	@Override
	public void startAsync(Task task) {
		if (taskToId.containsKey(task)) {
			throw new IllegalStateException("Task is already running");
		}
		int taskId;
		if (task.period() >= 1) {
			taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
				IntavePlugin.singletonInstance(),
				task::run,
				task.delay(), task.period()
			).getTaskId();
		} else {
			taskId = Bukkit.getScheduler().runTaskLaterAsynchronously(
				IntavePlugin.singletonInstance(),
				task::run,
				task.delay()
			).getTaskId();
		}
		taskToId.put(task, taskId);
	}

	@Override
	public void stop(Task task) {
		Integer taskId = taskToId.remove(task);
		if (taskId != null) {
			Bukkit.getScheduler().cancelTask(taskId);
		}
	}

	@Override
	public void stopAll() {
		taskToId.values().forEach(Bukkit.getScheduler()::cancelTask);
		taskToId.clear();
	}
}
