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
import de.jpx3.intave.module.linker.packetperfect.OnSend;
import de.jpx3.intave.packet.contract.immutable.ScoreboardDisplayPacket;
import de.jpx3.intave.packet.contract.immutable.ScoreboardObjectivePacket;
import de.jpx3.intave.packet.contract.immutable.ScoreboardScorePacket;
import de.jpx3.intave.packet.contract.immutable.ScoreboardTeamPacket;
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

import static de.jpx3.intave.packet.ListenerPriority.LAST;

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

  @OnSend(priority = LAST)
  public void sentObjective(User user, Cancellable event, ScoreboardObjectivePacket packet) {
    if (event.isCancelled()) {
      return;
    }
    ClientScoreboard scoreboard = user.meta().connection().clientScoreboard();
    if (packet.action() == ScoreboardObjectiveAction.REMOVE) {
      scoreboard.removeObjective(packet.objectiveName());
    } else {
      scoreboard.setObjective(packet.objectiveName(), packet.displayNameText());
    }
  }

  @OnSend(priority = LAST)
  public void sentDisplay(User user, Cancellable event, ScoreboardDisplayPacket packet) {
    if (event.isCancelled()) {
      return;
    }
    user.meta().connection().clientScoreboard().setDisplayObjective(packet.displaySlot(), packet.objectiveName());
  }

  @OnSend(priority = LAST)
  public void sentScore(User user, Cancellable event, ScoreboardScorePacket packet) {
    if (event.isCancelled()) {
      return;
    }
    ClientScoreboard scoreboard = user.meta().connection().clientScoreboard();
    if (packet.removed()) {
      scoreboard.resetScore(packet.owner(), packet.objectiveName());
    } else {
      Integer value = packet.value();
      scoreboard.setScore(packet.owner(), packet.objectiveName(), value == null ? 0 : value, packet.displayNameText());
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

  @OnSend(priority = LAST)
  public void sentTeam(User user, Cancellable event, ScoreboardTeamPacket packet) {
    if (event.isCancelled()) {
      return;
    }
    ClientScoreboard scoreboard = user.meta().connection().clientScoreboard();
    switch (packet.action()) {
      case CREATE:
        scoreboard.createTeam(packet.name(), packet.prefixText(), packet.suffixText(), packet.color(), packet.entries());
        break;
      case REMOVE:
        scoreboard.removeTeam(packet.name());
        break;
      case UPDATE:
        scoreboard.updateTeam(packet.name(), packet.prefixText(), packet.suffixText(), packet.color());
        break;
      case ADD_ENTRIES:
        scoreboard.addTeamEntries(packet.name(), packet.entries());
        break;
      case REMOVE_ENTRIES:
        scoreboard.removeTeamEntries(packet.name(), packet.entries());
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
