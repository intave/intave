package de.jpx3.intave.connect.shadow;

import de.jpx3.intave.user.User;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ShadowPacketDataLink {
  private final User user;
  private Map<Object, ShadowContext> movementLink = new ConcurrentHashMap<>(512);

  public ShadowPacketDataLink(User user) {
    this.user = user;
  }

  public void save(Object packet, ShadowContext data) {
    movementLink.put(packet, data);
  }

  public ShadowContext lookup(Object packet) {
    return movementLink.remove(packet);
  }
}
