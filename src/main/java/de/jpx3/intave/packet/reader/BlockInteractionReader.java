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

package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.MovingObjectPositionBlock;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.packet.plib.converter.PlayerHandConverter;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.user.User;
import org.bukkit.util.Vector;

public final class BlockInteractionReader extends BlockPositionReader {
  private final boolean MODERN_RESOLVE = MinecraftVersions.VER1_14_0.atOrAbove();
  private final boolean HAS_SEQUENCE_NUMBER = MinecraftVersions.VER1_19_2.atOrAbove();
  private final boolean SEPARATE_USE_ITEM_PACKET = MinecraftVersions.VER1_9_0.atOrAbove();

  @Nullable
  public PlayerHand hand() {
    if (!SEPARATE_USE_ITEM_PACKET) {
      return PlayerHand.MAIN_HAND;
    }
    return PlayerHandConverter.fromProtocolLib(packet().getHands().readSafely(0));
  }

  public boolean isItemUseRequest() {
    return SEPARATE_USE_ITEM_PACKET || enumDirection() == 255;
  }

  @Nullable
  public Direction direction() {
    int directionIndex = enumDirection();
    Direction[] directions = Direction.values();
    if (directionIndex < 0 || directionIndex >= directions.length) {
      return null;
    }
    return directions[directionIndex];
  }

  @Nullable
  public Vector facingVector() {
    StructureModifier<Float> floatsInPacket = packet().getFloat();
    if (floatsInPacket.size() >= 3) {
      return new Vector(
        floatsInPacket.read(0),
        floatsInPacket.read(1),
        floatsInPacket.read(2)
      );
    } else {
      return null;
    }
  }

  public int enumDirection() {
    if (MODERN_RESOLVE) {
      MovingObjectPositionBlock movingObjectPositionBlock = packet().getMovingBlockPositions().readSafely(0);
      return movingObjectPositionBlock == null ? 255 : movingObjectPositionBlock.getDirection().ordinal();
    } else {
      Integer enumDirection = packet().getIntegers().readSafely(0);
      if (enumDirection == null) {
        EnumWrappers.Direction direction = packet()
          .getDirections()
          .readSafely(0);
        return direction == null ? 255 : direction.ordinal();
      }
      return enumDirection;
    }
  }

  private int sequenceNumber = 0;
  private boolean hasArtificialSequenceNumber = false;

  public int sequenceNumber(User user) {
    if (HAS_SEQUENCE_NUMBER) {
      return packet().getIntegers().readSafely(0);
    } else if (hasArtificialSequenceNumber) {
      return sequenceNumber;
    } else {
      hasArtificialSequenceNumber = true;
      return sequenceNumber = user.meta().connection().simulatedBlockAckNum++;
    }
  }

  @Override
  public void release() {
    sequenceNumber = 0;
    hasArtificialSequenceNumber = false;
    super.release();
  }
}
