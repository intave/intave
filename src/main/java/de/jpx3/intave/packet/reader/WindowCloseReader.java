package de.jpx3.intave.packet.reader;

public class WindowCloseReader extends AbstractPacketReader {
  public int windowId() {
    return packet().getIntegers().read(0);
  }
}
