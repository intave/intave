package de.jpx3.intave.connect.cloud.protocol.listener;

import de.jpx3.intave.connect.cloud.protocol.Packet;
import de.jpx3.intave.connect.cloud.protocol.packets.*;

public interface Clientbound extends PacketListener {

  @Override
  default void onSelect(Packet<?> packet) {
    if (packet instanceof ClientboundHelloPacket) {
      onClientHello((ClientboundHelloPacket) packet);
    } else if (packet instanceof ClientboundDisconnectPacket) {
      onCloseConnection((ClientboundDisconnectPacket) packet);
    } else if (packet instanceof ClientboundCombatModifierPacket) {
      onCombatModifier((ClientboundCombatModifierPacket) packet);
    } else if (packet instanceof ClientboundDownloadStoragePacket) {
      onDownloadStorage((ClientboundDownloadStoragePacket) packet);
    } else if (packet instanceof ClientboundKeepAlivePacket) {
      onKeepAlive((ClientboundKeepAlivePacket) packet);
    } else if (packet instanceof ClientboundSetTrustfactorPacket) {
      onSetTrustfactor((ClientboundSetTrustfactorPacket) packet);
    } else if (packet instanceof ClientboundViolationPacket) {
      onViolation((ClientboundViolationPacket) packet);
    } else if (packet instanceof ClientboundShardsPacket) {
      onShardsPacket((ClientboundShardsPacket) packet);
    }
  }

  default void onClientHello(ClientboundHelloPacket packet) {
    onUncaught(packet);
  }

  default void onCloseConnection(ClientboundDisconnectPacket packet) {
    onUncaught(packet);
  }

  default void onCombatModifier(ClientboundCombatModifierPacket packet) {
    onUncaught(packet);
  }

  default void onDownloadStorage(ClientboundDownloadStoragePacket packet) {
    onUncaught(packet);
  }

  default void onKeepAlive(ClientboundKeepAlivePacket packet) {
    onUncaught(packet);
  }

  default void onSetTrustfactor(ClientboundSetTrustfactorPacket packet) {
    onUncaught(packet);
  }

  default void onShardsPacket(ClientboundShardsPacket packet) {
    onUncaught(packet);
  }

  default void onViolation(ClientboundViolationPacket packet) {
    onUncaught(packet);
  }

  default void onLogReceive(ClientboundLogReceivePacket packet) {
    onUncaught(packet);
  }

  default void onUncaught(Packet<?> packet) {

  }
}
