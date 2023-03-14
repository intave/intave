package de.jpx3.intave.module.nayoro.event.sink;

import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.nayoro.PlayerContainer;
import de.jpx3.intave.module.nayoro.event.Event;

public final class ForwardEventSink extends EventSink {
  private final PlayerContainer player;

  public ForwardEventSink(PlayerContainer player) {
    this.player = player;
  }

  @Override
  public void visitAny(Event event) {
    Modules.linker().nayoroEvents().fireEvent(player, event);
  }
}
