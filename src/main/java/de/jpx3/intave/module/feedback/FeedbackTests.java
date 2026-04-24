package de.jpx3.intave.module.feedback;

import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.test.Test;
import de.jpx3.intave.test.Tests;

public final class FeedbackTests extends Tests {
  private static final boolean USE_PING_PONG_PACKETS = MinecraftVersions.VER1_17_0.atOrAbove();

  public FeedbackTests() {
    super("FBK");
  }

  @Test
  public void createFeedbackPacket() {
    PacketWrapper<?> packet;
    if (USE_PING_PONG_PACKETS) {
      packet = new WrapperPlayServerPing(0);
    } else {
      packet = new WrapperPlayServerWindowConfirmation(0, (short) 0, false);
    }
    if (packet == null) {
      throw new AssertionError("feedback packet was not created");
    }
  }
}
