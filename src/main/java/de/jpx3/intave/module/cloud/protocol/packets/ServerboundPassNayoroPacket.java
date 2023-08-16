package de.jpx3.intave.module.cloud.protocol.packets;

import de.jpx3.intave.module.cloud.protocol.BinaryPacket;
import de.jpx3.intave.module.cloud.protocol.Identity;
import de.jpx3.intave.module.cloud.protocol.listener.Serverbound;

import java.io.DataInput;
import java.io.DataOutput;
import java.nio.ByteBuffer;

import static de.jpx3.intave.module.cloud.protocol.Direction.SERVERBOUND;

public final class ServerboundPassNayoroPacket extends BinaryPacket<Serverbound> {
  private Identity id;
  private ByteBuffer data;

  public ServerboundPassNayoroPacket() {
    super(SERVERBOUND, "PASS_NAYORO", "1");
  }

  public ServerboundPassNayoroPacket(Identity id, ByteBuffer data) {
    super(SERVERBOUND, "PASS_NAYORO", "1");
    this.id = id;
    this.data = data;
  }

  @Override
  public void serialize(DataOutput buffer) {
    try {
      id.serialize(buffer);
      byte[] array = data.array();
      buffer.write(array.length);
      buffer.write(array);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void deserialize(DataInput buffer) {
    try {
      id = Identity.from(buffer);
      int size = buffer.readInt();
      if (size > 1024 * 1024 * 50) {
        throw new RuntimeException("Too big");
      }
      byte[] array = new byte[size];
      buffer.readFully(array);
      data = ByteBuffer.wrap(array);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
