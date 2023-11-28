package de.jpx3.intave.module.feedback;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public class FeedbackRequest<T> {
  private final FeedbackCallback<T> callback;
  private final FeedbackObserver observer;
  private final T obj;
  private final short userKey;
  private final long key;
  private final long created;
  private final int options;

  FeedbackRequest(
    FeedbackCallback<T> callback,
    FeedbackObserver observer,
    T obj, short userKey,
    long key, int options
  ) {
    this.callback = callback;
    this.observer = observer;
    this.obj = obj;
    this.userKey = userKey;
    this.key = key;
    this.options = options;
    this.created = System.nanoTime();
  }

  void sent() {
    if (observer != null) {
      observer.sent(this);
    }
  }

  void acknowledge(Player player) {
    try {
      callback.success(player, obj);
      if (observer != null) {
        observer.received(this);
      }
    } catch (Exception e) {
      if (IntaveControl.DISABLE_LICENSE_CHECK) {
        IntaveLogger.logger().error("Error while acknowledging " + callback + " for " + player);
        e.printStackTrace();
      }
    }
  }

  T target() {
    return obj;
  }

  FeedbackCallback<T> callback() {
    return callback;
  }

  short userKey() {
    return userKey;
  }

  long num() {
    return key;
  }

  public long requestedAsNanos() {
    return created;
  }

  public long passedTime() {
    return passedTimeAs(TimeUnit.MILLISECONDS);
  }

  public long passedTimeAs(TimeUnit unit) {
    return unit.convert(System.nanoTime() - created, TimeUnit.NANOSECONDS);
  }

  public int options() {
    return options;
  }
}