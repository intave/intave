package de.jpx3.intave.executor.task;

import de.jpx3.intave.user.User;
import org.bukkit.Location;

public interface TaskScheduler {
	void startSync(Task task);

	void startUserSync(Task task, User user);

	/**
	 * Runs the task on the thread that owns the given location's region. On
	 * Folia this uses the {@code RegionScheduler} so the task may safely touch
	 * world/block state at that location; on regular servers it is equivalent
	 * to {@link #startSync(Task)} (everything runs on the single main thread).
	 */
	void startRegionSync(Task task, Location location);

	void startAsync(Task task);

	void stop(Task task);

	void stopAll();
}
