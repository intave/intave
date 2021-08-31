package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.common.collect.Lists;

import java.util.List;

public final class SingleBlockChangeReader extends AbstractPacketReader implements BlockChanges {
  @Override
  public List<BlockPosition> blockPositions() {
    return Lists.newArrayList(packet.getBlockPositionModifier().readSafely(0));
  }

  @Override
  public List<WrappedBlockData> blockDataList() {
    return Lists.newArrayList(packet.getBlockData().read(0));
  }
}
