package de.jpx3.intave.event.service.entity;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.List;

public final class LazyEntityCollisionService {
  private final IntavePlugin plugin;
  private static final double DISTANCE_TO_BOAT = 1.5f * 1.2;

  public LazyEntityCollisionService(IntavePlugin plugin) {
    this.plugin = plugin;
    if (IntaveControl.USE_BOAT_COLLISIONS) {
      this.setupBoatScheduler();
    }
  }

  private void setupBoatScheduler() {
    Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::synchronizeBoats, 0, 1);
  }

  private void synchronizeBoats() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      List<Entity> entities = player.getNearbyEntities(5, 5, 5);
      synchronizeBoatsOf(player, entities);
    }
  }

  private void synchronizeBoatsOf(Player player, List<Entity> entities) {
    if (!UserRepository.hasUser(player)) {
      return;
    }
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    boolean successful = false;
    for (Entity entity : entities) {
      if (entity.getType() == EntityType.BOAT) {
        Location entityLocation = entity.getLocation();
        double distance = movementData.distanceToVerifiedLocation(entityLocation);
        if (distance < DISTANCE_TO_BOAT) {
          movementData.nearestBoatLocation = entityLocation;
          successful = true;
        }
      }
    }
    if (!successful) {
      movementData.nearestBoatLocation = null;
    }
  }
}