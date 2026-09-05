package de.jpx3.intave.executor.task;

import de.jpx3.intave.user.User;
import org.bukkit.World;

interface TaskScheduler {
  void startSync(Task task);

  void startUserSync(Task task, User user);

  void startRegionSync(Task task, World world, int chunkX, int chunkZ);

  void startAsync(Task task);

  void stop(Task task);

  void stopAll();
}
