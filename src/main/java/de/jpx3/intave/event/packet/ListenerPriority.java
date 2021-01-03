package de.jpx3.intave.event.packet;

public enum ListenerPriority {

  // new

  FIRST(1),
  SECOND(2),
  THIRD(3),
  FORTH(4),
  FIFTH(5),
  SIXTH(6),
  SEVENTH(7),
  EIGHTH(8),
  NINTH(9),
  LAST(10),

  // legacy

  LOWEST(3),
  LOW(4),
  NORMAL(5),
  HIGH(6),
  HIGHEST(7),
  MONITOR(8)

  ;

  final int slot;

  ListenerPriority(int slot) {
    this.slot = slot;
  }

  public int slot() {
    return slot;
  }
}
