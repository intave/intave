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

import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.MovingObjectPositionBlock;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.packet.plib.converter.BlockPositionConverter;

public class BlockPositionReader extends AbstractPacketReader {
  private final boolean MODERN_RESOLVE = MinecraftVersions.VER1_14_0.atOrAbove();

  public @Nullable de.jpx3.intave.share.BlockPosition nativeBlockPosition() {
    BlockPosition blockPosition = blockPosition();
    return blockPosition == null
      ? null
      : de.jpx3.intave.share.BlockPosition.fromProtocolLib(blockPosition);
  }

  public @Nullable BlockPosition blockPosition() {
    if (!MODERN_RESOLVE) {
      return packet().getModifier()
        .withType(Lookup.serverClass("BlockPosition"), BlockPositionConverter.threadConverter())
        .readSafely(0);
    }
    BlockPosition directPosition = packet().getBlockPositionModifier().readSafely(0);
    if (directPosition != null) {
      return directPosition;
    }
    MovingObjectPositionBlock movingObjectPositionBlock = packet().getMovingBlockPositions().readSafely(0);
    return movingObjectPositionBlock == null ? null : movingObjectPositionBlock.getBlockPosition();
  }
}
