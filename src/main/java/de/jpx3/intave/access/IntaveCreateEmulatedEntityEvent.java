package de.jpx3.intave.access;

import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;

public class IntaveCreateEmulatedEntityEvent extends AbstractIntaveExternalEvent {
  protected WeakReference<Player> observer;
  protected int reservedEntityId;

  protected IntaveCreateEmulatedEntityEvent(Player observer, int reservedEntityId) {
    this.observer = new WeakReference<>(observer);
    this.reservedEntityId = reservedEntityId;
  }

  public final Player observer() {
    return observer.get();
  }

  public final int reservedEntityId() {
    return reservedEntityId;
  }

  public void copy(Player observer, int reservedEntityId) {
    this.observer = new WeakReference<>(observer);
    this.reservedEntityId = reservedEntityId;
  }

  @Override
  public void refClear() {
    this.observer = null;
  }

  public static IntaveCreateEmulatedEntityEvent empty() {
    return construct(null,0);
  }

  public static IntaveCreateEmulatedEntityEvent construct(Player observer, int reservedEntityId) {
    return new IntaveCreateEmulatedEntityEvent(observer, reservedEntityId);
  }
}
