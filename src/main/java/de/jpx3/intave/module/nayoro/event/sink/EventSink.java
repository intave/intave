package de.jpx3.intave.module.nayoro.event.sink;

import de.jpx3.intave.module.nayoro.event.*;

public abstract class EventSink {
  public void visitSelect(Event event) {
    if (event instanceof AttackEvent) {
      visit((AttackEvent) event);
    } else if (event instanceof ClickEvent) {
      visit((ClickEvent) event);
    } else if (event instanceof EntityMoveEvent) {
      visit((EntityMoveEvent) event);
    } else if (event instanceof PlayerInitEvent) {
      visit((PlayerInitEvent) event);
    } else if (event instanceof PlayerMoveEvent) {
      visit((PlayerMoveEvent) event);
    } else if (event instanceof SlotSwitchEvent) {
      visit((SlotSwitchEvent) event);
    } else if (event instanceof PropertiesEvent) {
      visit((PropertiesEvent) event);
    }
  }

  public void visit(PropertiesEvent event) {
    visitAny(event);
  }

  public void visit(AttackEvent event) {
    visitAny(event);
  }

  public void visit(ClickEvent event) {
    visitAny(event);
  }

  public void visit(EntitySpawnEvent event) {
    visitAny(event);
  }

  public void visit(EntityRemoveEvent event) {
    visitAny(event);
  }

  public void visit(EntityMoveEvent event) {
    visitAny(event);
  }

  public void visit(PlayerInitEvent event) {
    visitAny(event);
  }

  public void visit(PlayerMoveEvent event) {
    visitAny(event);
  }

  public void visit(SlotSwitchEvent event) {
    visitAny(event);
  }

  public void visitAny(Event event) {

  }

  public void close() {

  }
}
