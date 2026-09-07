package de.jpx3.intave.module.tracker.entity;

import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.view.PacketEventsTeamCollisionView;
import de.jpx3.intave.packet.view.ProtocolLibTeamCollisionView;
import de.jpx3.intave.packet.view.TeamCollisionView;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.SCOREBOARD_TEAM;

public final class EntityCollisionDisabler extends Module {
  private static final boolean DISABLE_ENTITY_COLLISIONS = MinecraftVersions.VER1_9_0.atOrAbove();

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsOut = {
      SCOREBOARD_TEAM
    }
  )
  public void receiveScoreboardUpdate(PacketEvent event) {
    handleScoreboardUpdate(new ProtocolLibTeamCollisionView(event));
  }

  /**
   * PacketEvents entry point for {@link #receiveScoreboardUpdate(PacketEvent)}.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGHEST,
    packetsOut = {
      SCOREBOARD_TEAM
    }
  )
  public void receiveScoreboardUpdate(PacketSendEvent event) {
    PacketEventsTeamCollisionView view = PacketEventsTeamCollisionView.of(event);
    if (view == null) {
      return;
    }
    handleScoreboardUpdate(view);
  }

  /** Engine independent collision rule override; see {@link TeamCollisionView}. */
  private void handleScoreboardUpdate(TeamCollisionView view) {
    if (!DISABLE_ENTITY_COLLISIONS) {
      return;
    }
    view.disableCollisions();
    view.release();
  }
}
