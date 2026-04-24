package de.jpx3.intave.module.tracker.entity;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.SCOREBOARD_TEAM;

public final class EntityCollisionDisabler extends Module {
  private static final boolean DISABLE_ENTITY_COLLISIONS = MinecraftVersions.VER1_9_0.atOrAbove();
  private static final int COLLISION_RULE_FIELD = (MinecraftVersions.VER1_13_0.atOrAbove() ? (MinecraftVersions.VER1_17_0.atOrAbove() ? 1 : 2) : 5);
  private static final boolean INDIRECT_SCOREBOARD_ACCESS = MinecraftVersions.VER1_17_0.atOrAbove();

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsOut = {
      SCOREBOARD_TEAM
    }
  )
  public void receiveScoreboardUpdate(ProtocolPacketEvent event, WrapperPlayServerTeams packet) {
    if (!DISABLE_ENTITY_COLLISIONS) {
      return;
    }
    if (packet.getTeamInfo().isPresent()) {
      packet.getTeamInfo().get().setCollisionRule(WrapperPlayServerTeams.CollisionRule.NEVER);
      event.markForReEncode(true);
    }
  }
}
