package de.jpx3.intave.tools.packet;

import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.access.IntaveInternalException;

public final class PlayerActionResolver {
  public static PlayerAction resolveActionFromPacket(PacketContainer packet) {
    for (Object value : packet.getModifier().getValues()) {
      for (PlayerAction playerAction : PlayerAction.values()) {
        if (playerAction.toString().equals(value.toString())) {
          return playerAction;
        }
      }
    }
    throw new IntaveInternalException("Could not read player action from packet");
  }
}