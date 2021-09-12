package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.wrappers.MovingObjectPositionBlock;
import de.jpx3.intave.adapter.MinecraftVersions;

public final class BlockInteractionReader extends BlockPositionReader {
  private final boolean MODERN_RESOLVE = MinecraftVersions.VER1_14_0.atOrAbove();
  public int enumDirection() {
    if (MODERN_RESOLVE) {
      MovingObjectPositionBlock movingObjectPositionBlock = packet.getMovingBlockPositions().readSafely(0);
      return movingObjectPositionBlock == null ? 255 : movingObjectPositionBlock.getDirection().ordinal();
    } else {
      Integer enumDirection = packet.getIntegers().readSafely(0);
      return enumDirection == null ? packet.getDirections().readSafely(0).ordinal() : enumDirection;
    }
  }
}
