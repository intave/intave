package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.player.collider.complex.ColliderResult;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;

import static de.jpx3.intave.math.MathHelper.distanceOf;

public final class Simulation {
  private static final Simulation INVALID_SIMULATION = new Simulation(MovementConfiguration.blank(), ColliderResult.invalid());
  private static final UserLocal<Simulation> SIMULATION_OBJ_CACHE = UserLocal.withInitial(Simulation::new);

  private MovementConfiguration configuration;
  private ColliderResult colliderResult;
  private String details = "";

	private Simulation() {
  }

  private Simulation(
    MovementConfiguration configuration,
    ColliderResult colliderResult
  ) {
    this.configuration = configuration;
    this.colliderResult = colliderResult;
  }

  public void flush(MovementConfiguration configuration, ColliderResult colliderResult) {
    this.configuration = configuration;
    this.colliderResult = colliderResult;
    this.details = "";
  }

  public boolean wasSprinting() {
    return configuration.isSprinting();
  }

  public double accuracy(Motion motionVector) {
    return distanceOf(motion(), motionVector);
  }

  public Motion motion() {
    return colliderResult.motion();
  }

  public void append(String details) {
    this.details += details;
  }

  public String details() {
    return details;
  }

  public boolean resultsInFlyingPacket(
    SimulationEnvironment environment,
    double limit
  ) {
    Position lastPosition = environment.lastPosition();
    Position newPosition = lastPosition.add(motion());
	  return lastPosition.distance(newPosition) < limit;
  }

  public ColliderResult collider() {
    return colliderResult;
  }

  @Deprecated
  public MovementConfiguration configuration() {
    return configuration;
  }

  public Simulation reusableCopy() {
    return new Simulation(configuration, colliderResult);
  }

  static Simulation of(User user, MovementConfiguration configuration, ColliderResult colliderResult) {
    Simulation simulation = SIMULATION_OBJ_CACHE.get(user);
    simulation.flush(configuration, colliderResult);
    return simulation;
  }

  static Simulation invalid() {
    return INVALID_SIMULATION;
  }
}
