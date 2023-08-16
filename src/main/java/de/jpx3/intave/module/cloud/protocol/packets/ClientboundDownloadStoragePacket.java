package de.jpx3.intave.module.cloud.protocol.packets;

import de.jpx3.intave.module.cloud.protocol.BinaryPacket;
import de.jpx3.intave.module.cloud.protocol.Direction;
import de.jpx3.intave.module.cloud.protocol.Identity;
import de.jpx3.intave.module.cloud.protocol.listener.Clientbound;

import java.io.DataInput;
import java.io.DataOutput;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

public final class ClientboundDownloadStoragePacket extends BinaryPacket<Clientbound> {
  private static final ThreadLocal<MessageDigest> digest =
    ThreadLocal.withInitial(() -> {
      try {
        return MessageDigest.getInstance("SHA-256");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

  private Identity id;
  private ByteBuffer data;

  public ClientboundDownloadStoragePacket() {
    super(Direction.CLIENTBOUND, "SET_STORAGE", "1");
  }

  public ClientboundDownloadStoragePacket(Identity id, ByteBuffer data) {
    super(Direction.CLIENTBOUND, "SET_STORAGE", "1");
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
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(array);
      buffer.write(digest.digest());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void deserialize(DataInput input) {
    try {
      id = Identity.from(input);
      int size = input.readInt();
      byte[] array = new byte[size];
      input.readFully(array);
      data = ByteBuffer.wrap(array);
      byte[] hash = new byte[32];
      input.readFully(hash);
      if (!MessageDigest.isEqual(hash, digest.get().digest(array))) {
        throw new RuntimeException("Hash mismatch");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
