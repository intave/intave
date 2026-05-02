package de.jpx3.intave.module.nayoro.detection;

import de.jpx3.intave.module.mitigate.AttackNerfStrategy;

final class MultiplexDetectionSubscription implements DetectionSubscription {
  private final DetectionSubscription[] subscriptions;

  public MultiplexDetectionSubscription(DetectionSubscription... subscriptions) {
    this.subscriptions = subscriptions;
  }

  @Override
  public void onDebug(String message) {
    for (DetectionSubscription subscription : subscriptions) {
      subscription.onDebug(message);
    }
  }

  @Override
  public void onNerf(AttackNerfStrategy strategy, String originCode) {
    for (DetectionSubscription subscription : subscriptions) {
      subscription.onNerf(strategy, originCode);
    }
  }
}
