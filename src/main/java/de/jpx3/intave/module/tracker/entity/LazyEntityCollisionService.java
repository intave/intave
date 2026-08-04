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

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class LazyEntityCollisionService extends Module {
  private static final double DISTANCE_TO_ENTITY = 1.5f * 1.2;

  /**
   * Entities a player can stand on and be carried by. Intave's simulator collides
   * against blocks only, so for these the prediction is simply wrong for as long as the
   * player is on one — we have them falling through it — and the evaluator tolerates
   * that instead (see the {@code collidedWithBoat} sites in SimulationEvaluator).
   * <p>
   * Matched by name rather than by constant because Intave compiles against a 1.12.2
   * API: {@code HAPPY_GHAST} (1.21.6) does not exist there, and neither do the
   * per-wood boat types of 1.19+.
   */
  private static final String[] STANDABLE_ENTITY_NAMES = {"BOAT", "HAPPY_GHAST"};
  private static final Set<EntityType> STANDABLE_ENTITIES = EnumSet.noneOf(EntityType.class);

  static {
    for (EntityType entity : EntityType.values()) {
      for (String standable : STANDABLE_ENTITY_NAMES) {
        if (entity.name().contains(standable)) {
          STANDABLE_ENTITIES.add(entity);
          break;
        }
      }
    }
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
      if (!STANDABLE_ENTITIES.contains(entity.getType())) {
        continue;
      }
      Location entityLocation = entity.getLocation();
      if (standingRange(user, entity, entityLocation)) {
        // The dimensions travel with the location: the range is re-tested at the point of
        // use (MovementMetadata.collidedWithBoat), where the entity is no longer reachable.
        movementData.nearestStandableWidth = entityWidth(entity);
        movementData.nearestStandableHeight = entityHeight(entity);
        movementData.nearestBoatLocation = entityLocation;
        entityFound = true;
      }
    }
    if (!entityFound) {
      movementData.nearestBoatLocation = null;
    }
  }

  /**
   * Whether the player is close enough to this entity to be standing on or against it.
   * <p>
   * A single radius around the entity's feet is only adequate for a boat. A happy ghast
   * is 4 blocks wide and 4 tall, so a player standing on one is around four blocks above
   * the location this compares against and would never have matched the flat 1.8 — which
   * is why standing on one mispredicted every tick. The entity's own dimensions decide
   * the range instead, horizontally and vertically apart, with a block of slack for the
   * carry.
   */
  private boolean standingRange(User user, Entity entity, Location entityLocation) {
    MovementMetadata movementData = user.meta().movement();
    double width = entityWidth(entity);
    double height = entityHeight(entity);

    double xDiff = Math.abs(movementData.verifiedLastPositionX - entityLocation.getX());
    double zDiff = Math.abs(movementData.verifiedLastPositionZ - entityLocation.getZ());
    double horizontal = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
    if (horizontal > width / 2 + DISTANCE_TO_ENTITY) {
      return false;
    }
    double yDiff = movementData.verifiedLastPositionY - entityLocation.getY();
    return yDiff > -DISTANCE_TO_ENTITY && yDiff < height + DISTANCE_TO_ENTITY;
  }

  private static final double BOAT_WIDTH = 1.375;
  private static final double BOAT_HEIGHT = 0.5625;

  private static final Method GET_WIDTH = resolveDimension("getWidth");
  private static final Method GET_HEIGHT = resolveDimension("getHeight");

  /**
   * Dimensions are read reflectively: {@code Entity#getWidth()/getHeight()} arrived
   * after the 1.12.2 API Intave compiles against. A server that cannot answer falls back
   * to a boat's size, which is the only thing this service covered before.
   */
  private double entityWidth(Entity entity) {
    return dimensionOf(GET_WIDTH, entity, BOAT_WIDTH);
  }

  private double entityHeight(Entity entity) {
    return dimensionOf(GET_HEIGHT, entity, BOAT_HEIGHT);
  }

  private double dimensionOf(Method accessor, Entity entity, double fallback) {
    if (accessor == null) {
      return fallback;
    }
    try {
      double value = ((Number) accessor.invoke(entity)).doubleValue();
      return value > 0 ? value : fallback;
    } catch (ReflectiveOperationException | ClassCastException unavailable) {
      return fallback;
    }
  }

  private static Method resolveDimension(String name) {
    try {
      return Entity.class.getMethod(name);
    } catch (NoSuchMethodException noDimensions) {
      return null;
    }
  }
}