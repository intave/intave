package de.jpx3.intave.packet.reader;

public interface ChunkCoordinateReader extends PacketReader {
  int[] xCoordinates();
  int[] zCoordinates();
}
