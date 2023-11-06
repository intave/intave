package de.jpx3.intave.module.tracker.entity;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PeriodicTickedEntitySelector {
  private final int ticks;
  private int taskId;

  public PeriodicTickedEntitySelector(int ticks) {
    this.ticks = ticks;
  }

  public void enableTask() {
    taskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(IntavePlugin.singletonInstance(), () -> {
      for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
        BackgroundExecutors.executeExternallyScheduled(() -> selectCappedEntities(onlinePlayer));
      }
    }, ticks, ticks);
    TaskTracker.begun(taskId);
  }

  public void disableTask() {
    Bukkit.getScheduler().cancelTask(taskId);
    TaskTracker.stopped(taskId);
  }

  public void selectCappedEntities(Player player) {
    User user = UserRepository.userOf(player);
    ConnectionMetadata connection = user.meta().connection();
    List<Entity> entities = new CopyOnWriteArrayList<>(connection.entities());
    Vector playerPosition = player.getLocation().toVector();
    for (Entity entity : entities) {
      entity.setTicked(false);
    }
    // remove dead entities
    entities.removeIf(entity -> !entity.isEntityAlive());
    if (entities.size() > 2500) {
      // remove entities that are too far away
      entities.removeIf(entity -> entity.distanceTo(playerPosition) > 64);
    }
    // sort by distance
    entities.sort(Comparator.comparingDouble(entity -> entity.distanceTo(playerPosition)));
    // cap collection size to 1000
    if (entities.size() > 1000) {
      entities = entities.subList(0, 1000);
    }
    for (Entity entity : entities) {
      entity.setTicked(true);
    }
//    System.out.println("Selected " + entities.size() + " entities for " + player.getName() + " (" + player.getUniqueId() + ")");
    connection.setTickedEntities(entities);
  }
}
