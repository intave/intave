package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.module.Modules;

public final class ForwardEventSink extends EventSink {
  private final PlayerContainer player;

  public ForwardEventSink(PlayerContainer player) {
    this.player = player;
  }

  @Override
  public void onAny(Event event) {
    Modules.linker().nayoroEvents().fireEvent(player, event);
  }
}
