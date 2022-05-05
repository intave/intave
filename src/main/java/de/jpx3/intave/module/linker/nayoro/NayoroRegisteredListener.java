package de.jpx3.intave.module.linker.nayoro;

import de.jpx3.intave.module.nayoro.Event;
import de.jpx3.intave.module.nayoro.PlayerContainer;

public final class NayoroRegisteredListener {
  private final NayoroEventSubscriber subscriber;
  private final NayoroEventExecutor eventExecutor;

  public NayoroRegisteredListener(NayoroEventSubscriber subscriber, NayoroEventExecutor eventExecutor) {
    this.subscriber = subscriber;
    this.eventExecutor = eventExecutor;
  }

  public void execute(PlayerContainer player, Event event) {
    eventExecutor.execute(subscriber, player, event);
  }

  public void initialize() {

  }

  public NayoroEventSubscriber subscriber() {
    return subscriber;
  }
}
