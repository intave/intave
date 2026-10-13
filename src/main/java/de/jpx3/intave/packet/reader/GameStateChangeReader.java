package de.jpx3.intave.packet.reader;

import de.jpx3.intave.share.GameStateChangeType;

public final class GameStateChangeReader extends AbstractPacketReader {

  public GameStateChangeType type() {
    GameStateChangeType type = GameStateChangeType.byId(typeIndex());
    return type == null ? GameStateChangeType.INVALID_BED : type;
  }

  private int typeIndex() {
    if (packet().getIntegers().size() > 0) {
      return packet().getIntegers().read(0);
    }
    return packet().getGameStateIDs().read(0);
  }

  public float value() {
    return packet().getFloat().read(0);
  }

  public int valueAsInt() {
    return (int)(value() + 0.5F);
  }
}
