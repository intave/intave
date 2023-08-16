package de.jpx3.intave.module.cloud.protocol.packets;

import de.jpx3.intave.module.cloud.protocol.BinaryPacket;
import de.jpx3.intave.module.cloud.protocol.listener.Clientbound;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static de.jpx3.intave.module.cloud.protocol.Direction.CLIENTBOUND;

public final class ClientboundKeepAlivePacket extends BinaryPacket<Clientbound> {
  private long returnTime;

  public ClientboundKeepAlivePacket() {
    super(CLIENTBOUND, "KEEP_ALIVE", "1");
  }

  @Override
  public void serialize(DataOutput buffer) {
    try {
      buffer.writeLong(returnTime);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void deserialize(DataInput buffer) {
    try {
      returnTime = buffer.readLong();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
