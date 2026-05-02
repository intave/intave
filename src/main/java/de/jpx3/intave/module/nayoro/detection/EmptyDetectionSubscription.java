package de.jpx3.intave.module.nayoro.detection;

import de.jpx3.intave.module.mitigate.AttackNerfStrategy;

final class EmptyDetectionSubscription implements DetectionSubscription {
  @Override
  public void onDebug(String message) {

  }

  @Override
  public void onNerf(AttackNerfStrategy strategy, String originCode) {

  }
}
