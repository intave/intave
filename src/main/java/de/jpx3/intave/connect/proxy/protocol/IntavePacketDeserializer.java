package de.jpx3.intave.connect.proxy.protocol;

import com.google.common.io.ByteArrayDataInput;

/**
 * Class generated using IntelliJ IDEA
 * Any distribution is strictly prohibited.
 * Copyright Richard Strunk 2019
 */

public final class IntavePacketDeserializer {
  public IntavePacket deserializeUsing(int packetId, ByteArrayDataInput dataInput) throws InstantiationException, IllegalAccessException, IllegalStateException {
    IntavePacket packet = PacketRegister.typeOfId(packetId).orElseThrow(IllegalStateException::new).newInstance();
    packet.applyFrom(dataInput);
    return packet;
  }
}
