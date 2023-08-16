package de.jpx3.intave.module.cloud.protocol;

import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.module.cloud.protocol.listener.Clientbound;
import de.jpx3.intave.module.cloud.protocol.listener.PacketListener;
import de.jpx3.intave.module.cloud.protocol.listener.Serverbound;

@KeepEnumInternalNames
public enum Direction {
  CLIENTBOUND(Clientbound.class),
  SERVERBOUND(Serverbound.class)

  ;

  private final Class<? extends PacketListener> listenerClass;

  Direction(Class<? extends PacketListener> listenerClass) {
    this.listenerClass = listenerClass;
  }

  public Class<? extends PacketListener> listenerClass() {
    return listenerClass;
  }

  public Direction opposite() {
    return this == CLIENTBOUND ? SERVERBOUND : CLIENTBOUND;
  }
}
