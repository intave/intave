package de.jpx3.intave.executor.task;

import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.user.User;

public final class Tasks {
	private static final boolean IS_FOLIA_SERVER = isFoliaServer();
	private static final TaskScheduler scheduler = IS_FOLIA_SERVER ? new FoliaTaskScheduler() : new BukkitTaskScheduler();

	public static Task delayed(
		Runnable runnable, long tickDelay
	) {
		return delayedNamed(null, runnable, tickDelay);
	}

	public static Task delayedNamed(
		String name, Runnable runnable, long tickDelay
	) {
		return new DelayedTask(name, runnable, tickDelay);
	}

	public static Task periodic(
		Runnable runnable,
		long tickDelay, long tickPeriod
	) {
		return periodicNamed(null, runnable, tickDelay, tickPeriod);
	}

	public static Task periodicNamed(
		String name, Runnable runnable, long tickDelay, long tickPeriod
	) {
		return new PeriodicTask(name, runnable, tickDelay, tickPeriod);
	}

	public static void addShutdownHook() {
		ShutdownTasks.add(scheduler::stopAll);
	}

	@Deprecated
	static void startSync(Task task) {
		scheduler.startSync(task);
	}

	static void startUserSync(Task task, User user) {
		scheduler.startUserSync(task, user);
	}

	static void startRegionSync(Task task, org.bukkit.Location location) {
		scheduler.startRegionSync(task, location);
	}

	static void startAsync(Task task) {
		scheduler.startAsync(task);
	}

	static void stop(Task task) {
		scheduler.stop(task);
	}

	private static boolean isFoliaServer() {
		// This is the officially correct way to check for Folia!
		// https://docs.papermc.io/paper/dev/folia-support/
		try {
			Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
}
