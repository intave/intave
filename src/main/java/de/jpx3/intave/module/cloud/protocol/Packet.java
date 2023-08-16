package de.jpx3.intave.module.cloud.protocol;

import de.jpx3.intave.module.cloud.protocol.listener.PacketListener;

public abstract class Packet<E extends PacketListener> implements Serializable {
  private final String name;
  private final String version;
  private final Direction direction;
  private final TransferMode mode;

  public Packet(Direction direction, String name, String version, TransferMode mode) {
    this.name = name;
    this.version = version;
    this.direction = direction;
    this.mode = mode;
  }

  public void accept(E listener) {
    listener.onAny(this);
  }

  public Direction direction() {
    return direction;
  }

  public String name() {
    return name;
  }

  public String version() {
    return version;
  }

  public TransferMode transferMode() {
    return mode;
  }
}
