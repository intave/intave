package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.module.dispatch.AttackDispatcher;
import de.jpx3.intave.module.tracker.entity.EntityShade;
import de.jpx3.intave.shade.Position;
import de.jpx3.intave.user.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class LiveEnvironment implements Environment {
  private final User user;
  private final UserPlayerContainer player;
  private final Map<String, BooleanSupplier> propertySupplier = new HashMap<>();

  public LiveEnvironment(User user) {
    this.user = user;
    this.player = new UserPlayerContainer(user);
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
  public PlayerContainer mainPlayer() {
    return player;
  }

  @Override
  public List<Integer> entities() {
    return user.meta().connection().entityIds();
  }

  @Override
  public Position positionOf(int id) {
    EntityShade.EntityPositionContext position = user.meta().connection().entityBy(id).position;
    return new Position(position.posX, position.posY, position.posZ);
  }

  @Override
  public boolean property(String name) {
    return propertySupplier.getOrDefault(name, () -> false).getAsBoolean();
  }

  @Override
  public long duration() {
    return -1;
  }

  @Override
  public long time() {
    return -1;
  }
}
