package de.jpx3.intave.module.tracker.entity;

import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.entity.size.HitboxSizeAccess;
import de.jpx3.intave.entity.type.RideableEntityType;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class LazyEntityCollisionService extends Module {
  private static final double DISTANCE_TO_ENTITY = 1.5f * 1.2;
  private static final Set<EntityType> BOAT_ENTITIES = EnumSet.noneOf(EntityType.class);

  static {
    for (EntityType entity : EntityType.values()) {
      RideableEntityType type = RideableEntityType.identifyFrom(entity.name());
      if (type == RideableEntityType.BOAT || type == RideableEntityType.CHEST_BOAT) {
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
  }

  @BukkitEventSubscription
  public void on(PlayerChangedWorldEvent event) {
    MovementMetadata movement = UserRepository.userOf(event.getPlayer()).meta().movement();
    movement.nearestBoatPosition = null;
    movement.nearestEntitySurface = null;
  }

  private void searchCollisions(User user, List<Entity> entities) {
    MovementMetadata movementData = user.meta().movement();
    Position nearestBoat = null;
    BoundingBox nearestSurface = null;
    double nearestBoatDistance = DISTANCE_TO_ENTITY;
    double nearestSurfaceDistance = DISTANCE_TO_ENTITY;
    for (Entity entity : entities) {
      if (BOAT_ENTITIES.contains(entity.getType())) {
        Position entityPosition = Position.of(entity.getLocation());
        double distance = movementData.distanceToVerifiedPosition(entityPosition);
        if (distance < nearestBoatDistance) {
          nearestBoat = entityPosition;
          nearestBoatDistance = distance;
        }
      } else if ("HAPPY_GHAST".equals(entity.getType().name())
        && entity instanceof Ageable && ((Ageable) entity).isAdult()
        && user.meta().protocol().protocolVersion() >= ProtocolMetadata.VER_1_21_6) {
        Location location = entity.getLocation();
        HitboxSize size = HitboxSizeAccess.dimensionsOfBukkit(entity);
        double halfWidth = size.width() / 2.0;
        double top = location.getY() + size.height();
        BoundingBox surface = new BoundingBox(
          location.getX() - halfWidth, top, location.getZ() - halfWidth,
          location.getX() + halfWidth, top, location.getZ() + halfWidth
        );
        double distance = movementData.distanceToEntitySurface(surface);
        if (distance < nearestSurfaceDistance) {
          nearestSurface = surface;
          nearestSurfaceDistance = distance;
        }
      }
    }
    movementData.nearestBoatPosition = nearestBoat;
    movementData.nearestEntitySurface = nearestSurface;
  }
}
