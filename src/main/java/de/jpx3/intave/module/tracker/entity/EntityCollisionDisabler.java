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

package de.jpx3.intave.module.tracker.entity;

import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.plib.reader.ScoreboardReader;

import static de.jpx3.intave.packet.contract.PacketType.Server.SCOREBOARD_TEAM;

public final class EntityCollisionDisabler extends Module {
  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsOut = {
      SCOREBOARD_TEAM
    }
  )
  public void receiveScoreboardUpdate(ScoreboardReader reader) {
    reader.disableTeamCollisions();
  }
}
