package de.jpx3.intave.connect.cloud.protocol.packets;

import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.connect.cloud.protocol.BinaryPacket;
import de.jpx3.intave.connect.cloud.protocol.Direction;
import de.jpx3.intave.connect.cloud.protocol.Identity;
import de.jpx3.intave.connect.cloud.protocol.listener.Serverbound;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.List;

public final class ServerboundUploadLogsPacket extends BinaryPacket<Serverbound> {
  private int packetNonce;
  private Identity identity;
  private List<String> logs;
  private Type type;

  public ServerboundUploadLogsPacket() {
    super(Direction.SERVERBOUND, "UPLOAD_LOG", "1");
  }

  public ServerboundUploadLogsPacket(Identity identity, int packetNonce, List<String> logs) {
    this();
    this.packetNonce = packetNonce;
    this.identity = identity;
    this.logs = logs;
  }

  @Override
  public void serialize(DataOutput buffer) {
    try {
      buffer.writeInt(packetNonce);
      if (identity != null) {
        buffer.writeBoolean(true);
        identity.serialize(buffer);
      } else {
        buffer.writeBoolean(false);
      }
      buffer.writeInt(logs.size());
      for (String log : logs) {
        buffer.writeUTF(log);
      }
      buffer.writeUTF(type.name());
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  @Override
  public void deserialize(DataInput buffer) {
    try {
      packetNonce = buffer.readInt();
      if (buffer.readBoolean()) {
        identity = Identity.from(buffer);
      }
      int logCount = buffer.readInt();
      for (int i = 0; i < logCount; i++) {
        logs.add(buffer.readUTF());
      }
      type = Type.valueOf(buffer.readUTF());
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  @KeepEnumInternalNames
  public enum Type {
    SERVER,
    PLAYER_VIOLATION,
    PACKET_INSPECTION,
    INTAVE_EXCEPTION,
  }
}
