package de.jpx3.intave.packet.reader;

public final class MapChunkBulkReader extends AbstractPacketReader implements ChunkCoordinateReader {
  @Override
  public int[] xCoordinates() {
    return packet.getIntegerArrays().read(0).clone();
  }

  @Override
  public int[] zCoordinates() {
    return packet.getIntegerArrays().read(1).clone();
  }
}
