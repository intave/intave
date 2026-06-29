package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.annotate.Immutable;
import de.jpx3.intave.annotate.Mutable;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;

public abstract class Simulator {
  public void simulateBetween(
    User user, MovementMetadata metadata,
    MovementConfiguration config
  ) {
    metadata.stepHeight = stepHeight();
    Motion motion = metadata.mutableBaseMotionCopy();
    simulatePreTick(
      user, motion, metadata
    );
    metadata.setBaseMotion(motion);
    metadata.refreshFriction(false);
    Simulation simulation = simulateTick(
      user, motion.copy(), metadata.unmodifiable(), config
    );
    metadata.assumeOccurred(simulation);
    motion = simulation.motion().copy();
    Position newPosition = metadata.verifiedLastPosition().add(motion);
    metadata.updateMovement(newPosition, Rotation.zero());
    simulateAfterTick(
      user, metadata, metadata.position(), motion
    );
    metadata.setBaseMotion(motion);
    metadata.lastOnGround = metadata.onGround;
    metadata.setVerifiedLastPosition(
      metadata.position(), "AUTOACCEPT"
    );
  }

  public abstract void simulatePreTick(
    User user,
    @Mutable Motion baseMotion,
    @Mutable SimulationEnvironment environment
  );

  /**
   * Simulate the entire movement until the position is updated.
   * This method is a function, so it does not change the metadata, the inputs or the motion.
   * It only returns the simulation result, which contains the new motion and collision results.
   */
  public abstract Simulation simulateTick(
    User user,
    @Immutable Motion motion,
    @Immutable SimulationEnvironment environment,
    @Immutable MovementConfiguration configuration
  );

  public abstract void simulateAfterTick(
    User user,
    @Mutable SimulationEnvironment environment,
    @Immutable Position position,
    @Mutable Motion motion
  );

  public abstract void setback(
    User user,
    SimulationEnvironment environment,
    double predictedX, double predictedY, double predictedZ
  );

  public float stepHeight() {
    return 0.6f;
  }

  public boolean affectedByMovementKeys() {
    return true;
  }
}