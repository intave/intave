package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.module.dispatch.AttackDispatcher;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class LiveEnvironment implements Environment {
  private final User user;
  private final UserPlayerContainer player;
  private final Map<String, BooleanSupplier> propertySupplier = new HashMap<>();

  public LiveEnvironment(User user) {
    this.user = user;
    this.player = new UserPlayerContainer(user);
    this.player.setEnvironment(this);
    setupProperties();
  }

  public void setupProperties() {
    setupProperty("reducingDisabled", () -> AttackDispatcher.REDUCING_DISABLED);
  }

  private void setupProperty(String name, BooleanSupplier supplier) {
    propertySupplier.put(name, supplier);
  }

  public Map<String, Boolean> properties() {
    Map<String, Boolean> properties = new HashMap<>();
    propertySupplier.forEach((name, supplier) -> properties.put(name, supplier.getAsBoolean()));
    return properties;
  }

  @Override
  public boolean entityMoved(int entityId, double distance) {
    Entity entity = user.meta().connection().entityBy(entityId);
    return entity != null && entity.moving(distance);
  }

  @Override
  public PlayerContainer mainPlayer() {
    return player;
  }

  @Override
  public Set<Integer> entities() {
    return user.meta().connection().entityIds();
  }

  @Override
  public Position positionOf(int id) {
    Entity.EntityPositionContext position = user.meta().connection().entityBy(id).position;
    return new Position(position.posX, position.posY, position.posZ);
  }

  // not used
  @Override
  public boolean inSight(int entity) {
    throw new UnsupportedOperationException("InSight not supported in live environment");
  }

  @Override
  public boolean property(String name) {
    return propertySupplier.getOrDefault(name, () -> false).getAsBoolean();
  }

  @Override
  public boolean hasPassed(long time) {
    return System.currentTimeMillis() > time;
  }
}
