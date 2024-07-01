package de.jpx3.intave.block.fluid;

import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;

public interface FluidFlow {
  boolean applyFlowTo(User user, BoundingBox boundingBox);

  double fluidDepthAt(User user, BoundingBox boundingBox);

  Motion pushMotionAt(User user, int blockX, int blockY, int blockZ);
}
