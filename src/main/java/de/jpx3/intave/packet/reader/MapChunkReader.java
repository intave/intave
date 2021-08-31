package de.jpx3.intave.packet.reader;

public final class MapChunkReader extends AbstractPacketReader implements ChunkCoordinateReader {
  @Override
  public int[] xCoordinates() {
    return new int[]{packet.getIntegers().read(0)};
  }

  @Override
  public int[] zCoordinates() {
    return new int[]{packet.getIntegers().read(1)};
  }
}
