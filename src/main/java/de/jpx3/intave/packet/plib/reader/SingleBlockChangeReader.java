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

import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.packet.plib.converter.BlockPositionConverter;

import java.util.Collections;
import java.util.List;

public final class SingleBlockChangeReader extends AbstractPacketReader implements BlockChanges {
  @Override
  public List<BlockPosition> blockPositions() {
    BlockPosition blockPosition = packet().getModifier()
      .withType(Lookup.serverClass("BlockPosition"), BlockPositionConverter.threadConverter())
      .read(0);
    return Collections.singletonList(blockPosition);
//    return Lists.newArrayList(packet.getBlockPositionModifier().readSafely(0));
  }

  @Override
  public List<WrappedBlockData> blockDataList() {
    return Collections.singletonList(packet().getBlockData().read(0));
  }
}
