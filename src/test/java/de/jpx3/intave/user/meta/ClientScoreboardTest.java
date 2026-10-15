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

package de.jpx3.intave.user.meta;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ClientScoreboardTest {
  @Test
  void reproducesScoreOrderTeamFormattingAndModernDisplayNames() {
    ClientScoreboard scoreboard = new ClientScoreboard();
    scoreboard.setObjective("match", "Match stats");
    scoreboard.setDisplayObjective("sidebar", "match");
    scoreboard.setScore("kills", "match", 8, null);
    scoreboard.setScore("deaths", "match", 2, "\u00a7cDeaths: 2");
    scoreboard.setScore("assists", "match", 5, null);
    scoreboard.createTeam(
      "kills-line", "Kills: ", "!", "green",
      Collections.singletonList("kills")
    );

    ClientScoreboard.Snapshot snapshot = scoreboard.snapshot("Player");

    assertEquals("Match stats", snapshot.title());
    assertEquals(
      Arrays.asList("\u00a7aKills: \u00a7akills!", "assists", "\u00a7cDeaths: 2"),
      snapshot.lines()
    );
  }

  @Test
  void omitsClientHiddenScoresBeforeApplyingTheFifteenLineLimit() {
    ClientScoreboard scoreboard = new ClientScoreboard();
    scoreboard.setObjective("board", "Title");
    scoreboard.setDisplayObjective("sidebar", "board");
    for (int score = 1; score <= 15; score++) {
      scoreboard.setScore("line-" + score, "board", score, null);
    }
    scoreboard.setScore("#hidden", "board", 100, null);

    ClientScoreboard.Snapshot snapshot = scoreboard.snapshot("Player");

    assertEquals(15, snapshot.lines().size());
    assertEquals("line-15", snapshot.lines().get(0));
    assertEquals("line-1", snapshot.lines().get(14));
  }

  @Test
  void selectsThePlayersColoredSidebarAndFallsBackAfterTeamRemoval() {
    ClientScoreboard scoreboard = new ClientScoreboard();
    scoreboard.setObjective("normal", "Normal");
    scoreboard.setObjective("red", "Red team");
    scoreboard.setDisplayObjective("sidebar", "normal");
    scoreboard.setDisplayObjective("team_red", "red");
    scoreboard.setScore("normal-line", "normal", 1, null);
    scoreboard.setScore("red-line", "red", 1, null);
    scoreboard.createTeam("players", "", "", "red", Collections.singletonList("Player"));

    assertEquals("Red team", scoreboard.snapshot("Player").title());
    assertEquals(Collections.singletonList("red-line"), scoreboard.snapshot("Player").lines());

    scoreboard.removeTeam("players");

    assertEquals("Normal", scoreboard.snapshot("Player").title());
    assertEquals(Collections.singletonList("normal-line"), scoreboard.snapshot("Player").lines());
  }

  @Test
  void appliesScoreAndObjectiveRemovalsAndLimitsTheSidebarToFifteenLines() {
    ClientScoreboard scoreboard = new ClientScoreboard();
    scoreboard.setObjective("board", "Title");
    scoreboard.setDisplayObjective("sidebar", "board");
    for (int score = 1; score <= 20; score++) {
      scoreboard.setScore("line-" + score, "board", score, null);
    }

    assertEquals(15, scoreboard.snapshot("Player").lines().size());
    assertEquals("line-20", scoreboard.snapshot("Player").lines().get(0));

    scoreboard.resetScore("line-20", "board");
    assertEquals("line-19", scoreboard.snapshot("Player").lines().get(0));
    scoreboard.resetScore("line-19", null);
    assertEquals("line-18", scoreboard.snapshot("Player").lines().get(0));

    scoreboard.removeObjective("board");
    assertNull(scoreboard.snapshot("Player").title());
    assertEquals(Collections.emptyList(), scoreboard.snapshot("Player").lines());
  }
}
