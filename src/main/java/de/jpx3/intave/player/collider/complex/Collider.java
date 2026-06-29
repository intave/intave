package de.jpx3.intave.player.collider.complex;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;

public interface Collider {
  ColliderResult collide(
    User user,
    SimulationEnvironment environment,
    Motion motion,
    double positionX, double positionY, double positionZ,
    boolean inWeb
  );
}