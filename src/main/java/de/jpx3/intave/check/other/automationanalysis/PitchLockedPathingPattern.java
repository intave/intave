package de.jpx3.intave.check.other.automationanalysis;

final class PitchLockedPathingPattern {
  private static final double PITCH_LOCKED_PATHING_MIN_DISTANCE = 20.0d;
  private static final double PITCH_LOCKED_PATHING_MIN_TICKS_PER_BLOCK = 3.0d;
  private static final double PITCH_LOCKED_PATHING_MIN_YAW_CHANGE = 45.0d;
  private static final double MIN_MOVE_DISTANCE = 1.0E-4d;
  private static final float MIN_PITCH_CHANGE = 1.0E-4f;

  private final PathingState pathingState = new PathingState();
  private MovementSample lastMovementSample;

  MovementResult pushMovement(double x, double y, double z, float yaw, float pitch) {
    if (!finite(x) || !finite(y) || !finite(z) || !finite(yaw) || !finite(pitch)) {
      clearMovement();
      return MovementResult.empty();
    }

    float wrappedYaw = wrapAngleTo180(yaw);
    MovementSample sample = new MovementSample(x, z, wrappedYaw, pitch);
    MovementSample previous = lastMovementSample;
    lastMovementSample = sample;
    if (previous == null) {
      return MovementResult.empty();
    }

    if (Math.abs(pitch) == 90.0f) {
      pathingState.clear();
      return MovementResult.empty();
    }

    double horizontalDistance = Math.hypot(sample.x - previous.x, sample.z - previous.z);
    if (horizontalDistance < MIN_MOVE_DISTANCE) {
      return MovementResult.empty();
    }

    float pitchDifference = Math.abs(sample.pitch - previous.pitch);
    if (pitchDifference > MIN_PITCH_CHANGE) {
      pathingState.clear();
      return MovementResult.empty();
    }

    pathingState.distance += horizontalDistance;
    pathingState.ticks++;
    pathingState.yawChange += Math.abs(wrapAngleTo180(sample.yaw - previous.yaw));

    if (pathingState.distance < PITCH_LOCKED_PATHING_MIN_DISTANCE) {
      return new MovementResult(
        false,
        pathingState.ticks,
        pathingState.distance,
        pathingState.yawChange,
        pathingState.ticks / pathingState.distance
      );
    }

    double ticksPerBlock = pathingState.ticks / pathingState.distance;
    MovementResult result = new MovementResult(
      ticksPerBlock >= PITCH_LOCKED_PATHING_MIN_TICKS_PER_BLOCK
        && pathingState.yawChange >= PITCH_LOCKED_PATHING_MIN_YAW_CHANGE,
      pathingState.ticks,
      pathingState.distance,
      pathingState.yawChange,
      ticksPerBlock
    );
    pathingState.clear();
    return result;
  }

  void clearMovement() {
    lastMovementSample = null;
    pathingState.clear();
  }

  private static boolean finite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }

  private static float wrapAngleTo180(float angle) {
    angle %= 360.0f;
    if (angle >= 180.0f) {
      angle -= 360.0f;
    }
    if (angle < -180.0f) {
      angle += 360.0f;
    }
    return angle;
  }

  static final class MovementResult {
    private static final MovementResult EMPTY = new MovementResult(false, 0, 0.0d, 0.0d, 0.0d);

    private final boolean suspicious;
    private final int ticks;
    private final double distance;
    private final double yawChange;
    private final double ticksPerBlock;

    private MovementResult(boolean suspicious, int ticks, double distance, double yawChange, double ticksPerBlock) {
      this.suspicious = suspicious;
      this.ticks = ticks;
      this.distance = distance;
      this.yawChange = yawChange;
      this.ticksPerBlock = ticksPerBlock;
    }

    static MovementResult empty() {
      return EMPTY;
    }

    boolean suspicious() {
      return suspicious;
    }

    int ticks() {
      return ticks;
    }

    double distance() {
      return distance;
    }

    double yawChange() {
      return yawChange;
    }

    double ticksPerBlock() {
      return ticksPerBlock;
    }
  }

  private static final class MovementSample {
    private final double x;
    private final double z;
    private final float yaw;
    private final float pitch;

    private MovementSample(double x, double z, float yaw, float pitch) {
      this.x = x;
      this.z = z;
      this.yaw = yaw;
      this.pitch = pitch;
    }
  }

  private static final class PathingState {
    private double distance;
    private double yawChange;
    private int ticks;

    void clear() {
      distance = 0.0d;
      yawChange = 0.0d;
      ticks = 0;
    }
  }
}
