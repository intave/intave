package de.jpx3.intave.module.nayoro.detection;

import de.jpx3.intave.module.mitigate.AttackNerfStrategy;

public interface DetectionSubscription {
  void onDebug(String message);
  void onNerf(AttackNerfStrategy strategy, String originCode);

  DetectionSubscription EMPTY = new EmptyDetectionSubscription();

  static DetectionSubscription empty() {
    return EMPTY;
  }

  static DetectionSubscription merge(DetectionSubscription... subscriptions) {
    if (subscriptions.length == 0) {
      return EMPTY;
    } else if (subscriptions.length == 1) {
      return subscriptions[0];
    } else {
      return new MultiplexDetectionSubscription(subscriptions);
    }
  }
}
