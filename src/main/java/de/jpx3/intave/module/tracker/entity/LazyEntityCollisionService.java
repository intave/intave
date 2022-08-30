package de.jpx3.intave.module.tracker.entity;

import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayList;
import java.util.List;

public final class LazyEntityCollisionService extends Module {
  private static final double DISTANCE_TO_ENTITY = 1.5f * 1.2;
  private static final List<EntityType> BOAT_ENTITIES = new ArrayList<>();

  static {
    for (EntityType entity : EntityType.values()) {
      if (entity.name().contains("BOAT")) {
        BOAT_ENTITIES.add(entity);
      }
    }
  }

  @BukkitEventSubscription
  public void on(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    List<Entity> entities = player.getNearbyEntities(5, 5, 5);
    searchCollisions(user, entities);
    if (!user.hasPlayer()) {
      user.kick("Please reconnect");
    }
  }

  private void searchCollisions(User user, List<Entity> entities) {
    MovementMetadata movementData = user.meta().movement();
    boolean entityFound = false;
    for (Entity entity : entities) {
      if (!BOAT_ENTITIES.contains(entity.getType())) {
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
}