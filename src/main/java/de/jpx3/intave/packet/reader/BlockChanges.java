package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;

import java.util.List;

public interface BlockChanges extends PacketReader {
  List<BlockPosition> blockPositions();
  List<WrappedBlockData> blockDataList();
}
