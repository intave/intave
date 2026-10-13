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

package de.jpx3.intave.module.tracker.player;

import ac.intave.samples.event.EventSink;
import ac.intave.samples.event.ScoreboardEvent;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.task.Task;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.PrioritySlot;
import de.jpx3.intave.packet.reader.ScoreboardReader;
import de.jpx3.intave.packet.reader.ScoreboardReader.Display;
import de.jpx3.intave.packet.reader.ScoreboardReader.Objective;
import de.jpx3.intave.packet.reader.ScoreboardReader.Score;
import de.jpx3.intave.packet.reader.ScoreboardReader.Team;
import de.jpx3.intave.share.ScoreboardObjectiveAction;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ClientScoreboard;
import de.jpx3.intave.user.meta.ClientScoreboard.Snapshot;
import org.bukkit.event.Cancellable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.RESET_SCORE;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.SCOREBOARD_DISPLAY_OBJECTIVE;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.SCOREBOARD_OBJECTIVE;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.SCOREBOARD_SCORE;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.SCOREBOARD_TEAM;

/** Mirrors outgoing scoreboard packets and samples the visible sidebar. */
public final class PlayerScoreboardRecorder extends Module {
  private final UserLocal<EmissionState> emissionStates =
    UserLocal.withInitial(EmissionState::new);
  private Task task;

  @Override
  public void enable() {
    task = Tasks.periodicNamed(
      "PlayerScoreboardRecorder.flush",
      () -> UserRepository.applyOnOnlineUsers(user ->
        Synchronizer.synchronize(user, () -> flush(user))
      ),
      40, 40
    ).startAsync();
  }

  @Override
  public void disable() {
    if (task != null) {
      task.cancel();
      task = null;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.MONITOR,
    prioritySlot = PrioritySlot.EXTERNAL,
    packetsOut = {
      SCOREBOARD_OBJECTIVE, SCOREBOARD_DISPLAY_OBJECTIVE,
      SCOREBOARD_SCORE, RESET_SCORE, SCOREBOARD_TEAM
    }
  )
  public void receive(User user, Cancellable packet, ScoreboardReader reader) {
    if (packet.isCancelled()) {
      return;
    }
    ClientScoreboard scoreboard = user.meta().connection().clientScoreboard();
    switch (reader.kind()) {
      case SCOREBOARD_OBJECTIVE:
        Objective objective = reader.objective();
        if (objective.action == ScoreboardObjectiveAction.REMOVE) {
          scoreboard.removeObjective(objective.name);
        } else {
          scoreboard.setObjective(objective.name, objective.title);
        }
        break;
      case SCOREBOARD_DISPLAY_OBJECTIVE:
        Display display = reader.display();
        scoreboard.setDisplayObjective(display.slot, display.objective);
        break;
      case SCOREBOARD_SCORE:
        applyScore(scoreboard, reader.score());
        break;
      case RESET_SCORE:
        applyScore(scoreboard, reader.resetScore());
        break;
      case SCOREBOARD_TEAM:
        applyTeam(scoreboard, reader.team());
        break;
      default:
        throw new IllegalStateException("Unexpected scoreboard packet " + reader.kind());
    }
  }

  public void start(User user, EventSink sink, Runnable attachSink) {
    Snapshot snapshot = snapshot(user);
    for (ScoreboardEvent event : eventsBetween(Snapshot.empty(), snapshot)) {
      event.accept(sink);
    }
    emissionStates.get(user).last = snapshot;
    attachSink.run();
  }

  private void flush(User user) {
    Snapshot current = snapshot(user);
    EmissionState state = emissionStates.get(user);
    for (ScoreboardEvent event : eventsBetween(state.last, current)) {
      Modules.nayoro().emit(user, event);
    }
    state.last = current;
  }

  private Snapshot snapshot(User user) {
    return user.meta().connection().clientScoreboard().snapshot(user.player().getName());
  }

  private static void applyScore(ClientScoreboard scoreboard, Score score) {
    if (score.remove) {
      scoreboard.resetScore(score.owner, score.objective);
    } else {
      scoreboard.setScore(score.owner, score.objective, score.value, score.displayName);
    }
  }

  private static void applyTeam(ClientScoreboard scoreboard, Team team) {
    switch (team.action) {
      case CREATE:
        scoreboard.createTeam(team.name, team.prefix, team.suffix, team.color, team.entries);
        break;
      case REMOVE:
        scoreboard.removeTeam(team.name);
        break;
      case UPDATE:
        scoreboard.updateTeam(team.name, team.prefix, team.suffix, team.color);
        break;
      case ADD_ENTRIES:
        scoreboard.addTeamEntries(team.name, team.entries);
        break;
      case REMOVE_ENTRIES:
        scoreboard.removeTeamEntries(team.name, team.entries);
        break;
      default:
        break;
    }
  }

  static List<ScoreboardEvent> eventsBetween(Snapshot previous, Snapshot current) {
    boolean titleChanged = !Objects.equals(previous.title(), current.title());
    String title = current.title() == null ? "" : current.title();
    int lineCount = Math.max(previous.lines().size(), current.lines().size());
    List<Integer> changedLines = new ArrayList<>();
    for (int line = 0; line < lineCount; line++) {
      String oldText = textAt(previous, line);
      String newText = textAt(current, line);
      if (!oldText.equals(newText)) {
        changedLines.add(line);
      }
    }

    List<ScoreboardEvent> events = new ArrayList<>();
    for (int i = 0; i < changedLines.size(); i++) {
      int line = changedLines.get(i);
      String text = textAt(current, line);
      if (i == 0 && titleChanged) {
        events.add(new ScoreboardEvent(line, text, title));
        titleChanged = false;
      } else {
        events.add(new ScoreboardEvent(line, text));
      }
    }
    if (titleChanged) {
      events.add(new ScoreboardEvent(title));
    }
    return events;
  }

  private static String textAt(Snapshot snapshot, int line) {
    return line < snapshot.lines().size() ? snapshot.lines().get(line) : "";
  }

  private static final class EmissionState {
    private Snapshot last = Snapshot.empty();
  }
}
