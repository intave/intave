package de.jpx3.intave.module.cloud.protocol.packets;

import de.jpx3.intave.module.cloud.protocol.BinaryPacket;
import de.jpx3.intave.module.cloud.protocol.Direction;
import de.jpx3.intave.module.cloud.protocol.listener.Serverbound;

import java.io.DataInput;
import java.io.DataOutput;
import java.nio.ByteBuffer;

public final class ServerboundConfirmEncryptionPacket extends BinaryPacket<Serverbound> {
  private ByteBuffer encryptedSharedSecret;
  private ByteBuffer encryptedVerifyToken;

  public ServerboundConfirmEncryptionPacket() {
    super(Direction.SERVERBOUND, "CONFIRM_ENCRYPTION", "1");
  }

  public ServerboundConfirmEncryptionPacket(byte[] encryptedSharedSecret, byte[] encryptedVerifyToken) {
    super(Direction.SERVERBOUND, "CONFIRM_ENCRYPTION", "1");
    this.encryptedSharedSecret = ByteBuffer.wrap(encryptedSharedSecret);
    this.encryptedVerifyToken = ByteBuffer.wrap(encryptedVerifyToken);
  }

  @Override
  public void serialize(DataOutput buffer) {
    try {
      byte[] shared = encryptedSharedSecret.array();
      buffer.writeInt(shared.length);
      buffer.write(shared);
      byte[] verify = encryptedVerifyToken.array();
      buffer.writeInt(verify.length);
      buffer.write(verify);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void deserialize(DataInput buffer) {

  }
}
