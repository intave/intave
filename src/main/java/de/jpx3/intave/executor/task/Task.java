package de.jpx3.intave.executor.task;

import de.jpx3.intave.user.User;
import org.bukkit.Location;

public interface Task {
	void run();

	default String name() {
		return getClass().getSimpleName();
	}

	default long delay() {
		return 0;
	}

	default long period() {
		return -1;
	}

	default Task startAsync() {
		Tasks.startAsync(this);
		return this;
	}

	@Deprecated
	default Task startSync() {
		Tasks.startSync(this);
		return this;
	}

	default Task startUserSync(User user) {
		Tasks.startUserSync(this, user);
		return this;
	}

	default Task startRegionSync(Location location) {
		Tasks.startRegionSync(this, location);
		return this;
	}

	default Task cancel() {
		Tasks.stop(this);
		return this;
	}
}
