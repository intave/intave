package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.module.dispatch.AttackDispatcher;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class LiveEnvironment implements Environment {
  private final User user;
  private final UserPlayerContainer player;
  private final Map<String, Supplier<String>> propertySupplier = new HashMap<>();

  public LiveEnvironment(User user) {
    this.user = user;
    this.player = new UserPlayerContainer(user, this);
    setupProperties();
  }

  public void setupProperties() {
    setupProperty("reducingDisabled", () -> Boolean.toString(AttackDispatcher.REDUCING_DISABLED));
  }

  private void setupProperty(String name, Supplier<String> supplier) {
    propertySupplier.put(name, supplier);
  }

  public Map<String, String> properties() {
    Map<String, String> properties = new HashMap<>();
    propertySupplier.forEach((name, supplier) -> properties.put(name, supplier.get()));
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
  @Nullable
  public Position positionOf(int id) {
    Entity entity = user.meta().connection().entityBy(id);
    if (entity == null) {
      return null;
    }
    return entity.position.toPosition();
  }

  // not used
  @Override
  public boolean inSight(int entity) {
    throw new UnsupportedOperationException("InSight not supported in live environment");
  }

  @Override
  public boolean property(String name) {
    return Boolean.parseBoolean(propertySupplier.getOrDefault(name, () -> "false").get());
  }

  @Override
  public long currentTime() {
    return System.currentTimeMillis();
  }
}
