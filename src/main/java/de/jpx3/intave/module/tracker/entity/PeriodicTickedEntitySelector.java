package de.jpx3.intave.module.tracker.entity;

import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.task.Task;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Deprecated
public final class PeriodicTickedEntitySelector {
  private final int ticks;
  private Task task;

  public PeriodicTickedEntitySelector(int ticks) {
    this.ticks = ticks;
  }

  public void enableTask() {
    task = Tasks.periodicNamed("PeriodicTickedEntitySelector.select", () -> {
      UserRepository.applyOnOnlineUsers(user ->
        Synchronizer.synchronize(user, () -> selectCappedEntities(user.player()))
      );
    }, ticks, ticks).startAsync();
  }

  public void disableTask() {
    if (task != null) {
      task.cancel();
      task = null;
    }
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
    connection.setTickedEntities(entities);
  }
}
