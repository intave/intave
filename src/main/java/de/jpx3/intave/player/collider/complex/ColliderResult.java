package de.jpx3.intave.player.collider.complex;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.share.Motion;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ColliderResult {
  private static final ColliderResult INVALID_SIMULATION = new ColliderResult(
    new Motion(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE), null, false, false, false, false, false, false, false, 0);

  private final Motion motion;
  private final Motion intermittentResult;
  private final boolean onGround, collidedHorizontally, collidedVertically;
  private final boolean resetMotionX, resetMotionZ;
  private final boolean step, edgeSneak;

  private final double yStepHeight;

  private final Map<String, Double> debugData = IntaveControl.ENABLE_MOVEMENT_DEBUGGER_COLLECTOR ? new HashMap<>() : Collections.emptyMap();

  public ColliderResult(
    Motion motion,
    Motion intermittentResult,
    boolean onGround,
    boolean collidedHorizontally, boolean collidedVertically,
    boolean resetMotionX, boolean resetMotionZ,
    boolean step, boolean edgeSneak,
    double yStepHeight
  ) {
    if (motion == null) {
      throw new IllegalArgumentException("Context cannot be null");
    }
    this.motion = motion;
    this.intermittentResult = intermittentResult;
    this.onGround = onGround;
    this.collidedHorizontally = collidedHorizontally;
    this.collidedVertically = collidedVertically;
    this.resetMotionX = resetMotionX;
    this.resetMotionZ = resetMotionZ;
    this.step = step;
    this.edgeSneak = edgeSneak;
    this.yStepHeight = yStepHeight;
  }

  public double accuracy(Motion motionVector) {
    return MathHelper.distanceOf(motion, motionVector);
  }

  public Motion motion() {
    return motion;
  }

  public Motion intermittentResult() {
    return intermittentResult;
  }

  public boolean onGround() {
    return onGround;
  }

  public boolean collidedHorizontally() {
    return collidedHorizontally;
  }

  public boolean collidedVertically() {
    return collidedVertically;
  }

  public boolean step() {
    return step;
  }

  public boolean resetMotionX() {
    return resetMotionX;
  }

  public boolean resetMotionZ() {
    return resetMotionZ;
  }

  public boolean edgeSneak() {
    return edgeSneak;
  }

  public double stepHeightThisMove() {
    return yStepHeight;
  }

  public void applyTo(
    SimulationEnvironment environment
  ) {
    if (environment == null) {
      throw new IllegalArgumentException("Environment cannot be null");
    }
  }

  public void debugAttach(String key, double value) {
    if (IntaveControl.ENABLE_MOVEMENT_DEBUGGER_COLLECTOR) {
      debugData.put(key, value);
    }
  }

  public Map<String, Double> debugData() {
    return debugData;
  }

  public static ColliderResult invalid() {
    return INVALID_SIMULATION;
  }

  public static ColliderResult untouched(Motion motion) {
    return new ColliderResult(motion, null,false, false, false, false, false, false, false, 0);
  }
}