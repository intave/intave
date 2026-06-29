package de.jpx3.intave.player.collider.simple;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;

public interface SimpleCollider {
  @Deprecated
  SimpleColliderResult collide(
    User user, BoundingBox boundingBox,
    double motionX, double motionY, double motionZ
  );

  SimpleColliderResult collide(
    User user, SimulationEnvironment environment,
    BoundingBox boundingBox, Motion motion
  );
}
