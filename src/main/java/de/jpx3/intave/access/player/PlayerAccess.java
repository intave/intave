package de.jpx3.intave.access.player;

import de.jpx3.intave.access.player.trust.TrustFactor;

public interface PlayerAccess {
  int protocolVersion();

  default double violationLevel(String check) {
    return violationLevel(check, "thresholds");
  }
  double violationLevel(String check, String threshold);

  default void addViolationPoints(String check, double amount) {
    addViolationPoints(check, "thresholds", amount);
  }
  void addViolationPoints(String check, String threshold, double amount);

  default void resetViolationLevel(String check) {
    resetViolationLevel(check, "thresholds");
  }
  void resetViolationLevel(String check, String threshold);

  TrustFactor trustFactor();
  @Deprecated
  void setTrustFactor(TrustFactor factor);

  PlayerClicks clicks();
  PlayerConnection connection();
}
