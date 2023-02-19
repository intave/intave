package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.events.PacketContainer;

public abstract class CompiledPacketReader extends AbstractPacketReader {
  @Override
  public void enter(PacketContainer packet) {
    super.enter(packet);
    compile();
  }

  public abstract void compile();
}
