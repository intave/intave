package de.jpx3.intave.module.cloud.protocol.listener;

import de.jpx3.intave.module.cloud.protocol.Packet;
import de.jpx3.intave.module.cloud.protocol.packets.ServerboundConfirmEncryptionPacket;
import de.jpx3.intave.module.cloud.protocol.packets.ServerboundHelloPacket;
import de.jpx3.intave.module.cloud.protocol.packets.ServerboundRequestStoragePacket;

public interface Serverbound extends PacketListener {
  @Override
  default void onAny(Packet<?> packet) {
    if (packet instanceof ServerboundConfirmEncryptionPacket) {
      onConfirmEncryption((ServerboundConfirmEncryptionPacket)packet);
    } else if (packet instanceof ServerboundRequestStoragePacket) {
      onRequestStorage((ServerboundRequestStoragePacket)packet);
    } else if (packet instanceof ServerboundHelloPacket) {
      onHello((ServerboundHelloPacket)packet);
    }
  }

  default void onHello(ServerboundHelloPacket packet) {

  }

  default void onConfirmEncryption(ServerboundConfirmEncryptionPacket packet) {

  }

  default void onRequestStorage(ServerboundRequestStoragePacket packet) {

  }
}
