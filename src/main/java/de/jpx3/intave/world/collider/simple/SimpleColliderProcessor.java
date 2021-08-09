package de.jpx3.intave.world.collider.simple;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.User;

public interface SimpleColliderProcessor {
  SimpleColliderSimulationResult collide(
    User user, WrappedAxisAlignedBB boundingBox,
    double motionX, double motionY, double motionZ
  );
}
