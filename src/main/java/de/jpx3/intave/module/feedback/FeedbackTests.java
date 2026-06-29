package de.jpx3.intave.module.feedback;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.test.IntegrationTests;
import de.jpx3.intave.test.Test;

import static com.comphenix.protocol.PacketType.Play.Server.PING;
import static com.comphenix.protocol.PacketType.Play.Server.TRANSACTION;

public final class FeedbackTests extends IntegrationTests {
  private static final boolean USE_PING_PONG_PACKETS = MinecraftVersions.VER1_17_0.atOrAbove();

  public FeedbackTests() {
    super("FBK");
  }

  @Test
  public void createFeedbackPacket() {
    ProtocolManager protocol = ProtocolLibrary.getProtocolManager();
    PacketContainer packet;
    if (USE_PING_PONG_PACKETS) {
      packet = protocol.createPacket(PING);
      packet.getIntegers().write(0, 0);
    } else {
      packet = protocol.createPacket(TRANSACTION);
      packet.getIntegers().write(0, 0);
      packet.getShorts().write(0, (short)0);
      packet.getBooleans().write(0, false);
    }
    // the purpur error appears on "write" so if we pass until here we're fine
  }
}
