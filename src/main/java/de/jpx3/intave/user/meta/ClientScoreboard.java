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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A packet-fed copy of the scoreboard state visible to one client. */
public final class ClientScoreboard {
  private static final int MAX_SIDEBAR_LINES = 15;
  private static final String[] COLORS = {
    "black", "dark_blue", "dark_green", "dark_aqua",
    "dark_red", "dark_purple", "gold", "gray",
    "dark_gray", "blue", "green", "aqua",
    "red", "light_purple", "yellow", "white"
  };
  private static final char[] COLOR_CODES = "0123456789abcdef".toCharArray();

  private final Map<String, String> objectives = new HashMap<>();
  private final Map<String, String> displayObjectives = new HashMap<>();
  private final Map<String, Map<String, Score>> scores = new HashMap<>();
  private final Map<String, Team> teams = new HashMap<>();
  private final Map<String, String> teamsByEntry = new HashMap<>();

  public synchronized void setObjective(String name, String title) {
    objectives.put(name, title);
  }

  public synchronized void removeObjective(String name) {
    objectives.remove(name);
    scores.remove(name);
    displayObjectives.values().removeIf(name::equals);
  }

  public synchronized void setDisplayObjective(String slot, String objective) {
    if (objective == null || objective.isEmpty()) {
      displayObjectives.remove(slot);
    } else {
      displayObjectives.put(slot, objective);
    }
  }

  public synchronized void setScore(
    String owner, String objective, int value, String displayName
  ) {
    scores.computeIfAbsent(objective, ignored -> new HashMap<>())
      .put(owner, new Score(owner, value, displayName));
  }

  public synchronized void resetScore(String owner, String objective) {
    if (objective != null && !objective.isEmpty()) {
      Map<String, Score> objectiveScores = scores.get(objective);
      if (objectiveScores != null) {
        objectiveScores.remove(owner);
      }
      return;
    }
    for (Map<String, Score> objectiveScores : scores.values()) {
      objectiveScores.remove(owner);
    }
  }

  public synchronized void createTeam(
    String name, String prefix, String suffix, String color,
    List<String> entries
  ) {
    removeTeam(name);
    Team team = new Team(prefix, suffix, color);
    teams.put(name, team);
    addTeamEntries(name, entries);
  }

  public synchronized void updateTeam(
    String name, String prefix, String suffix, String color
  ) {
    Team team = teams.get(name);
    if (team == null) {
      team = new Team(prefix, suffix, color);
      teams.put(name, team);
    } else {
      team.prefix = prefix;
      team.suffix = suffix;
      team.color = color;
    }
  }

  public synchronized void removeTeam(String name) {
    Team removed = teams.remove(name);
    if (removed == null) {
      return;
    }
    for (String entry : removed.entries) {
      teamsByEntry.remove(entry, name);
    }
  }

  public synchronized void addTeamEntries(String name, List<String> entries) {
    Team team = teams.computeIfAbsent(name, ignored -> new Team("", "", "reset"));
    for (String entry : entries) {
      String previousName = teamsByEntry.put(entry, name);
      if (previousName != null && !previousName.equals(name)) {
        Team previous = teams.get(previousName);
        if (previous != null) {
          previous.entries.remove(entry);
        }
      }
      team.entries.add(entry);
    }
  }

  public synchronized void removeTeamEntries(String name, List<String> entries) {
    Team team = teams.get(name);
    if (team == null) {
      return;
    }
    for (String entry : entries) {
      team.entries.remove(entry);
      teamsByEntry.remove(entry, name);
    }
  }

  public synchronized Snapshot snapshot(String playerName) {
    String objective = visibleObjective(playerName);
    if (objective == null) {
      return Snapshot.empty();
    }
    String title = objectives.get(objective);
    if (title == null) {
      return Snapshot.empty();
    }
    Map<String, Score> objectiveScores = scores.get(objective);
    if (objectiveScores == null || objectiveScores.isEmpty()) {
      return new Snapshot(title, Collections.emptyList());
    }

    List<Score> ordered = new ArrayList<>(objectiveScores.values());
    ordered.sort(
      Comparator.comparingInt((Score score) -> score.value).reversed()
        .thenComparing(score -> score.owner, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(score -> score.owner)
    );
    List<String> lines = new ArrayList<>(Math.min(ordered.size(), MAX_SIDEBAR_LINES));
    for (Score score : ordered) {
      if (score.owner.startsWith("#")) {
        continue;
      }
      lines.add(render(score));
      if (lines.size() == MAX_SIDEBAR_LINES) {
        break;
      }
    }
    return new Snapshot(title, lines);
  }

  private String visibleObjective(String playerName) {
    String teamName = teamsByEntry.get(playerName);
    Team team = teamName == null ? null : teams.get(teamName);
    if (team != null) {
      String colored = displayObjectives.get("team_" + normalizeColor(team.color));
      if (colored != null) {
        return colored;
      }
    }
    return displayObjectives.get("sidebar");
  }

  private String render(Score score) {
    if (score.displayName != null) {
      return score.displayName;
    }
    String teamName = teamsByEntry.get(score.owner);
    Team team = teamName == null ? null : teams.get(teamName);
    if (team == null) {
      return score.owner;
    }
    String teamColor = colorCode(team.color);
    return teamColor + team.prefix + teamColor + score.owner + team.suffix;
  }

  private static String normalizeColor(String color) {
    return color == null ? "reset" : color.toLowerCase(Locale.ROOT);
  }

  private static String colorCode(String color) {
    String normalized = normalizeColor(color);
    for (int i = 0; i < COLORS.length; i++) {
      if (COLORS[i].equals(normalized)) {
        return "\u00a7" + COLOR_CODES[i];
      }
    }
    return "";
  }

  public static String legacyDisplaySlot(int slot) {
    if (slot == 1) {
      return "sidebar";
    }
    int color = slot - 3;
    return color >= 0 && color < COLORS.length ? "team_" + COLORS[color] : "other_" + slot;
  }

  public static String legacyColor(int color) {
    return color >= 0 && color < COLORS.length ? COLORS[color] : "reset";
  }

  private static final class Score {
    private final String owner;
    private final int value;
    private final String displayName;

    private Score(String owner, int value, String displayName) {
      this.owner = owner;
      this.value = value;
      this.displayName = displayName;
    }
  }

  private static final class Team {
    private String prefix;
    private String suffix;
    private String color;
    private final Set<String> entries = new LinkedHashSet<>();

    private Team(String prefix, String suffix, String color) {
      this.prefix = prefix;
      this.suffix = suffix;
      this.color = color;
    }
  }

  public static final class Snapshot {
    private static final Snapshot EMPTY = new Snapshot(null, Collections.emptyList());

    private final String title;
    private final List<String> lines;

    public Snapshot(String title, List<String> lines) {
      this.title = title;
      this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public static Snapshot empty() {
      return EMPTY;
    }

    public String title() {
      return title;
    }

    public List<String> lines() {
      return lines;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof Snapshot)) {
        return false;
      }
      Snapshot snapshot = (Snapshot) object;
      return Objects.equals(title, snapshot.title) && lines.equals(snapshot.lines);
    }

    @Override
    public int hashCode() {
      return Objects.hash(title, lines);
    }
  }
}
