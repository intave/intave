package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.wrappers.EnumWrappers;

public final class BlockPlaceReader extends BlockPositionReader {
  public int enumDirection() {
    Integer enumDirection = packet.getIntegers().readSafely(0);
    if (enumDirection == null) {
      EnumWrappers.Direction direction = packet.getDirections().readSafely(0);
      enumDirection = direction == null ? 255 : direction.ordinal();
    }
    return enumDirection;
  }
}
