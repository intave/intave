package de.jpx3.intave.check.movement.physics.eval;

import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.share.Direction.AxisDirection.POSITIVE;

final class UncertaintyParameters {
  private static final UncertaintyParameters EMPTY = new UncertaintyParameters();
  private static final UserLocal<UncertaintyParameters> USER_CACHE = UserLocal.withInitial(UncertaintyParameters::new);

  private final List<Evaluator> modifiers = new ArrayList<>();
  private double positiveXUncertainty = 0.0;
  private double positiveXMultiplier = 1.0;
  private double negativeXUncertainty = 0.0;
  private double negativeXMultiplier = 1.0;

  private double positiveYUncertainty = 0.0;
  private double positiveYMultiplier = 1.0;
  private double negativeYUncertainty = 0.0;
  private double negativeYMultiplier = 1.0;

  private double positiveZUncertainty = 0.0;
  private double positiveZMultiplier = 1.0;
  private double negativeZUncertainty = 0.0;
  private double negativeZMultiplier = 1.0;

  public void horizontalUncertainty(Evaluator evaluator, double value) {
    positiveXUncertainty = Math.max(positiveXUncertainty, value);
    negativeXUncertainty = Math.min(negativeXUncertainty, value);
    positiveZUncertainty = Math.max(positiveZUncertainty, value);
    negativeZUncertainty = Math.min(negativeZUncertainty, value);
    modifiers.add(evaluator);
  }

  public void verticalUncertainty(Evaluator evaluator, double value) {
    positiveYUncertainty = Math.max(positiveYUncertainty, value);
    negativeYUncertainty = Math.min(negativeYUncertainty, value);
    modifiers.add(evaluator);
  }

  public void horizontalMultiplierOverride(Evaluator evaluator, double value) {
    positiveXMultiplier = value;
    negativeXMultiplier = value;
    positiveZMultiplier = value;
    negativeZMultiplier = value;
    modifiers.add(evaluator);
  }

  public void verticalMultiplierOverride(Evaluator evaluator, double value) {
    positiveYMultiplier = value;
    negativeYMultiplier = value;
    modifiers.add(evaluator);
  }

  public double uncertaintyOf(Direction direction) {
    Direction.Axis axis = direction.axis();
    Direction.AxisDirection axisDirection = direction.axisDirection();
    switch (axis) {
      case X_AXIS:
        return axisDirection == POSITIVE ? positiveXUncertainty : negativeXUncertainty;
      case Y_AXIS:
        return axisDirection == POSITIVE ? positiveYUncertainty : negativeYUncertainty;
      case Z_AXIS:
        return axisDirection == POSITIVE ? positiveZUncertainty : negativeZUncertainty;
      default:
        throw new IllegalArgumentException("Unknown axis: " + axis);
    }
  }

  @Deprecated
  public double uncertaintyOf(Direction.Plane plane) {
    switch (plane) {
      case HORIZONTAL:
        return Math.max(Math.max(positiveXUncertainty, negativeXUncertainty), Math.max(positiveZUncertainty, negativeZUncertainty));
      case VERTICAL:
        return Math.max(positiveYUncertainty, negativeYUncertainty);
      default:
        throw new IllegalArgumentException("Unknown plane: " + plane);
    }
  }

  public double multiplierOf(Direction direction) {
    Direction.Axis axis = direction.axis();
    Direction.AxisDirection axisDirection = direction.axisDirection();
    switch (axis) {
      case X_AXIS:
        return axisDirection == POSITIVE ? positiveXMultiplier : negativeXMultiplier;
      case Y_AXIS:
        return axisDirection == POSITIVE ? positiveYMultiplier : negativeYMultiplier;
      case Z_AXIS:
        return axisDirection == POSITIVE ? positiveZMultiplier : negativeZMultiplier;
      default:
        throw new IllegalArgumentException("Unknown axis: " + axis);
    }
  }

  @Deprecated
  public double multiplierOf(Direction.Plane plane) {
    switch (plane) {
      case HORIZONTAL:
        return Math.max(Math.max(positiveXMultiplier, negativeXMultiplier), Math.max(positiveZMultiplier, negativeZMultiplier));
      case VERTICAL:
        return Math.max(positiveYMultiplier, negativeYMultiplier);
      default:
        throw new IllegalArgumentException("Unknown plane: " + plane);
    }
  }

  public void reset() {
    modifiers.clear();
    positiveXUncertainty = 0.0;
    positiveXMultiplier = 1.0;
    negativeXUncertainty = 0.0;
    negativeXMultiplier = 1.0;
    positiveZUncertainty = 0.0;
    positiveZMultiplier = 1.0;
    negativeZUncertainty = 0.0;
    negativeZMultiplier = 1.0;
    positiveYUncertainty = 0.0;
    positiveYMultiplier = 1.0;
    negativeYUncertainty = 0.0;
    negativeYMultiplier = 1.0;
  }

  public List<Evaluator> modifiers() {
    return modifiers;
  }

  public static UncertaintyParameters empty() {
    return EMPTY;
  }

  public static UncertaintyParameters of(User user) {
    return USER_CACHE.get(user);
  }
}
