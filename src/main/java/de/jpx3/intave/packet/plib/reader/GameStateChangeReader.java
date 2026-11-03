/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.packet.plib.reader;

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
