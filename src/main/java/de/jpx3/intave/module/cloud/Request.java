package de.jpx3.intave.module.cloud;

import de.jpx3.intave.access.player.trust.TrustFactor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Request<TARGET> {
  private final long created;
  private final List<Consumer<TARGET>> subscribers = new ArrayList<>();

  public Request() {
    this.created = System.currentTimeMillis();
  }

  public void subscribe(Consumer<TARGET> consumer) {
    subscribers.add(consumer);
  }

  public void publish(TARGET target) {
    subscribers.forEach(consumer -> consumer.accept(target));
  }

  public long duration() {
    return System.currentTimeMillis() - created;
  }
}
