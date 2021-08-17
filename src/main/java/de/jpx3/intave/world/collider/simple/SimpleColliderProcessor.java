package de.jpx3.intave.world.collider.simple;

import de.jpx3.intave.user.User;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;

public interface SimpleColliderProcessor {
  SimpleColliderSimulationResult collide(
    User user, WrappedAxisAlignedBB boundingBox,
    double motionX, double motionY, double motionZ
  );
}
