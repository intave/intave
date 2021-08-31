package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.adapter.MinecraftVersions;

import java.util.ArrayList;
import java.util.List;

public final class MultiBlockChangeReader extends CompiledPacketReader implements BlockChanges {
  private final static boolean USE_SELECTION_POSITION = MinecraftVersions.VER1_16_2.atOrAbove();
  private List<BlockPosition> blockPositions;
  private List<WrappedBlockData> blockDataList;

  public void compile() {
    if (USE_SELECTION_POSITION) {
      BlockPosition blockPosition = packet.getSectionPositions().readSafely(0);
      int chunkXBase = blockPosition.getX() << 4;
      int chunkYBase = blockPosition.getY() << 4;
      int chunkZBase = blockPosition.getZ() << 4;
      short[] relativePositions = packet.getShortArrays().read(0);
      WrappedBlockData[] blockInfos = packet.getBlockDataArrays().read(0);
      int expectedOutputLength = blockInfos.length;
      blockPositions = new ArrayList<>(expectedOutputLength);
      blockDataList = new ArrayList<>(expectedOutputLength);
      for (int i = 0; i < relativePositions.length; i++) {
        short relativePosition = relativePositions[i];
        int posX = chunkXBase + (relativePosition >>> 8 & 0xF);
        int posY = chunkYBase + (relativePosition & 0xF);
        int posZ = chunkZBase + (relativePosition >>> 4 & 0xF);
        blockPositions.add(new BlockPosition(posX, posY, posZ));
        blockDataList.add(blockInfos[i]);
      }
    } else {
      MultiBlockChangeInfo[] multiBlockChangeInfos = packet.getMultiBlockChangeInfoArrays().readSafely(0);
      int expectedOutputLength = multiBlockChangeInfos.length;
      blockPositions = new ArrayList<>(expectedOutputLength);
      blockDataList = new ArrayList<>(expectedOutputLength);
      for (MultiBlockChangeInfo changeInfo : multiBlockChangeInfos) {
        blockPositions.add(new BlockPosition(changeInfo.getAbsoluteX(), changeInfo.getY(), changeInfo.getAbsoluteZ()));
        blockDataList.add(changeInfo.getData());
      }
    }
  }

  @Override
  public void close() {
    super.close();
    blockPositions = null;
    blockDataList = null;
  }

  public List<BlockPosition> blockPositions() {
    return blockPositions;
  }

  public List<WrappedBlockData> blockDataList() {
    return blockDataList;
  }
}
