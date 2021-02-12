package de.jpx3.intave.access;

import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.UUID;

public final class IntaveCreateEmulatedPlayerEvent extends IntaveCreateEmulatedEntityEvent {
  private String name;
  private UUID id;

  protected IntaveCreateEmulatedPlayerEvent(
    Player observer, int reservedEntityId,
    String name, UUID id
  ) {
    super(observer, reservedEntityId);
    this.name = name;
    this.id = id;
  }

  public String name() {
    return name;
  }

  public UUID id() {
    return id;
  }

  public void copy(Player observer, int reservedEntityId, String name, UUID id) {
    this.observer = new WeakReference<>(observer);
    this.reservedEntityId = reservedEntityId;
    this.name = name;
    this.id = id;
  }

  @Override
  public void refClear() {
    this.observer = null;
  }

  public static IntaveCreateEmulatedPlayerEvent empty() {
    return construct(null,0, "error", null);
  }

  public static IntaveCreateEmulatedPlayerEvent construct(Player observer, int reservedEntityId, String name, UUID id) {
    return new IntaveCreateEmulatedPlayerEvent(observer, reservedEntityId, name, id);
  }
}
