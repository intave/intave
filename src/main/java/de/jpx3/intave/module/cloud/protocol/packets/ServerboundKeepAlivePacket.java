package de.jpx3.intave.module.cloud.protocol.packets;

import de.jpx3.intave.module.cloud.protocol.BinaryPacket;
import de.jpx3.intave.module.cloud.protocol.listener.Serverbound;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static de.jpx3.intave.module.cloud.protocol.Direction.SERVERBOUND;

public final class ServerboundKeepAlivePacket extends BinaryPacket<Serverbound> {
  private long time;

  public ServerboundKeepAlivePacket() {
    super(SERVERBOUND, "KEEP_ALIVE", "1");
  }

  @Override
  public void serialize(DataOutput buffer) {
    try {
      buffer.writeLong(System.currentTimeMillis());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void deserialize(DataInput buffer) {
    try {
      time = buffer.readLong();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
