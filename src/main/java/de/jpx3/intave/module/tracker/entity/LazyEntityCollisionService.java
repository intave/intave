package de.jpx3.intave.module.tracker.entity;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.List;

public final class LazyEntityCollisionService implements BukkitEventSubscriber {
  private static final double DISTANCE_TO_ENTITY = 1.5f * 1.2;

  public LazyEntityCollisionService(IntavePlugin plugin) {
    plugin.eventLinker().registerEventsIn(this);
  }

  @BukkitEventSubscription
  public void on(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    List<Entity> entities = player.getNearbyEntities(5, 5, 5);
    searchCollisions(user, entities);
  }

  private void searchCollisions(User user, List<Entity> entities) {
    MovementMetadata movementData = user.meta().movement();
    boolean entityFound = false;
    for (Entity entity : entities) {
      if (!collidableEntity(entity.getType())) {
        continue;
      }
      Location entityLocation = entity.getLocation();
      double distance = movementData.distanceToVerifiedLocation(entityLocation);
      if (distance < DISTANCE_TO_ENTITY) {
        movementData.nearestBoatLocation = entityLocation;
        entityFound = true;
      }
    }
    if (!entityFound) {
      movementData.nearestBoatLocation = null;
    }
  }

  private boolean collidableEntity(EntityType entityType) {
    return entityType == EntityType.BOAT;
  }
}