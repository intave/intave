package de.jpx3.intave.access;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * The common superclass for all <i>custom events</i> pushed to the Bukkit event pipeline by Intave.
 */
public abstract class IntaveEvent extends Event {
  private static final HandlerList handlers = new HandlerList();

  public IntaveEvent() {
    super(true);
  }

  public abstract void referenceInvalidate();

  public HandlerList getHandlers() {
    return handlers;
  }

  public static HandlerList getHandlerList() {
    return handlers;
  }
}
