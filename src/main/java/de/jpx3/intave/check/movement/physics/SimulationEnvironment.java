package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.shade.Motion;
import de.jpx3.intave.shade.Position;
import org.bukkit.util.Vector;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2021
 */

public interface SimulationEnvironment {
  /**
   * pose
   * @return
   */
  Pose pose();

  /**
   * look vector
   * @return
   */
  Vector lookVector();

  default Position position() {
    return new Position(positionX(), positionY(), positionZ());
  }
  double positionX();
  double positionY();
  double positionZ();

  /**
   * verified position
   * @return
   */
  default Position verifiedPosition() {
    return new Position(verifiedPositionX(), verifiedPositionY(), verifiedPositionZ());
  }
  double verifiedPositionX();
  double verifiedPositionY();
  double verifiedPositionZ();

  /**
   * last position
   * @return
   */
  default Position lastPosition() {
    return new Position(lastPositionX(), lastPositionY(), lastPositionZ());
  }
  double lastPositionX();
  double lastPositionY();
  double lastPositionZ();

  default Motion motion() {
    return new Motion(motionX(), motionY(), motionZ());
  }
  double motionX();
  double motionY();
  double motionZ();

  Vector motionMultiplier();

  float yawSine();
  float yawCosine();

  float aiMoveSpeed(boolean sprinting);
  float friction();
  double resetMotion();
  double jumpMotion();
  double gravity();

  // states
  boolean isSneaking();
  boolean inWater();
  boolean inLava();
  boolean inWeb();
  boolean onGround();
  boolean lastOnGround();
}
