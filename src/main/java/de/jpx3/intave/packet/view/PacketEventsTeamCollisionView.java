/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.packet.view;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;

import java.util.Optional;

/**
 * {@link TeamCollisionView} backed by PacketEvents.
 * <p>
 * PacketEvents models the team parameters as an optional block on every protocol version, so the
 * version branching the ProtocolLib view carries collapses into one presence check here: the block
 * is there exactly when the packet's mode is a create or an update, which are the only modes that
 * serialise a collision rule.
 * <p>
 * The rewrite is flushed as a re-encode in {@link #release()} and only when the rule actually
 * changed, so a packet that already said "never" - or one that carries no rule at all - is passed
 * through untouched rather than round tripped through the wrapper.
 */
public final class PacketEventsTeamCollisionView implements TeamCollisionView {

  private final PacketSendEvent event;
  private final WrapperPlayServerTeams wrapper;

  private boolean dirty;

  private PacketEventsTeamCollisionView(PacketSendEvent event, WrapperPlayServerTeams wrapper) {
    this.event = event;
    this.wrapper = wrapper;
  }

  /** @return a view over the event, or null when the packet is not a scoreboard team update. */
  public static PacketEventsTeamCollisionView of(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.TEAMS) {
      return null;
    }
    return new PacketEventsTeamCollisionView(event, new WrapperPlayServerTeams(event));
  }

  @Override
  public void disableCollisions() {
    Optional<WrapperPlayServerTeams.ScoreBoardTeamInfo> teamInfo = wrapper.getTeamInfo();
    if (teamInfo == null || !teamInfo.isPresent()) {
      return;
    }
    WrapperPlayServerTeams.ScoreBoardTeamInfo info = teamInfo.get();
    if (info.getCollisionRule() == WrapperPlayServerTeams.CollisionRule.NEVER) {
      return;
    }
    info.setCollisionRule(WrapperPlayServerTeams.CollisionRule.NEVER);
    dirty = true;
  }

  @Override
  public void release() {
    if (!dirty) {
      return;
    }
    event.markForReEncode(true);
    dirty = false;
  }
}
