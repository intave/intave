package de.jpx3.intave.check.movement.physics.eval;

import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;

public final class EvaluationResult {
  private static final EvaluationResult EMPTY = new EvaluationResult();
  private static final UserLocal<EvaluationResult> USER_LOCAL = UserLocal.withInitial(EvaluationResult::empty);

  private double horizontalVL;
  private double verticalVL;

  private boolean strictMode;

  private EvaluationResult() {
    super();
  }

  private EvaluationResult(double horizontalVL, double verticalVL) {
    super();
    this.horizontalVL = horizontalVL;
    this.verticalVL = verticalVL;
  }

  private EvaluationResult(double horizontalVL, double verticalVL, boolean strictMode) {
    super();
    this.horizontalVL = horizontalVL;
    this.verticalVL = verticalVL;
    this.strictMode = strictMode;
  }

  public void setHorizontalVL(double horizontalVL) {
    this.horizontalVL = horizontalVL;
  }

  public void setVerticalVL(double verticalVL) {
    this.verticalVL = verticalVL;
  }

  public void setStrictMode(boolean strictMode) {
    this.strictMode = strictMode;
  }

  public double horizontalVL() {
    return horizontalVL;
  }

  public double verticalVL() {
    return verticalVL;
  }

  public boolean strictMode() {
    return strictMode;
  }

  public void reset() {
    this.horizontalVL = 0;
    this.verticalVL = 0;
    this.strictMode = false;
  }

  public static EvaluationResult get(User user) {
    return USER_LOCAL.get(user);
  }

  public static EvaluationResult empty() {
    return EMPTY;
  }
}
