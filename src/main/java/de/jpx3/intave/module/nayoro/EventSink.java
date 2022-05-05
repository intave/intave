package de.jpx3.intave.module.nayoro;

public abstract class EventSink {
  public void on(AttackEvent event) {
    onAny(event);
  }

  public void on(ClickEvent event) {
    onAny(event);
  }

  public void on(EntityMoveEvent event) {
    onAny(event);
  }

  public void on(MoveEvent event) {
    onAny(event);
  }

  public void on(SlotSwitchEvent event) {
    onAny(event);
  }

  public abstract void onAny(Event event);

  public void close() {

  }
}
