package de.jpx3.intave.packet.reader;

import de.jpx3.intave.share.PlayerAction;
import de.jpx3.intave.packet.plib.converter.PlayerActionResolver;

public final class PlayerActionReader extends AbstractPacketReader {
  public PlayerAction playerAction() {
    return PlayerActionResolver.resolveActionFromPacket(packet());
  }
}
