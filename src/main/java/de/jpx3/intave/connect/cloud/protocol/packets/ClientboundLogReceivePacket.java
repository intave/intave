package de.jpx3.intave.connect.cloud.protocol.packets;

import de.jpx3.intave.connect.cloud.protocol.BinaryPacket;
import de.jpx3.intave.connect.cloud.protocol.Direction;
import de.jpx3.intave.connect.cloud.protocol.Identity;
import de.jpx3.intave.connect.cloud.protocol.listener.Clientbound;

import java.io.DataInput;
import java.io.DataOutput;

public final class ClientboundLogReceivePacket extends BinaryPacket<Clientbound> {
  private int packetNonceResult;
  private Identity id;
  private String logId;

  public ClientboundLogReceivePacket() {
    super(Direction.CLIENTBOUND, "RECEIVE_LOG", "1");
  }

  public Identity id() {
    return id;
  }

  public int packetNonceResult() {
    return packetNonceResult;
  }

  public String logId() {
    return logId;
  }

  @Override
  public void serialize(DataOutput buffer) {
    try {
      buffer.writeInt(packetNonceResult);
      if (id != null) {
        buffer.writeBoolean(true);
        id.serialize(buffer);
      } else {
        buffer.writeBoolean(false);
      }
      buffer.writeUTF(logId);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  @Override
  public void deserialize(DataInput buffer) {
    try {
      packetNonceResult = buffer.readInt();
      if (buffer.readBoolean()) {
        id = Identity.from(buffer);
      }
      logId = buffer.readUTF();
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }
}
