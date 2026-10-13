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

package de.jpx3.intave.module.nayoro;

import ac.intave.samples.event.Event;
import ac.intave.samples.event.EventSink;
import ac.intave.samples.event.TimeEvent;
import ac.intave.samples.event.WeatherEvent;
import ac.intave.samples.share.ClockState;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.PrioritySlot;
import de.jpx3.intave.packet.reader.GameStateChangeReader;
import de.jpx3.intave.share.GameStateChangeType;
import de.jpx3.intave.packet.reader.TimeUpdateReader;
import de.jpx3.intave.packet.reader.TimeUpdateReader.Update;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;
import org.bukkit.event.Cancellable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

/** Records the time and weather packets sent to each player. */
public final class PlayerEnvironmentRecorder implements PacketEventSubscriber {
  private final UserLocal<State> states = UserLocal.withInitial(State::new);
  private final BiConsumer<? super User, ? super Event> emitter;

  PlayerEnvironmentRecorder(BiConsumer<? super User, ? super Event> emitter) {
    this.emitter = emitter;
  }

  @PacketSubscription(priority = ListenerPriority.MONITOR, prioritySlot = PrioritySlot.EXTERNAL, packetsOut = GAME_STATE_CHANGE)
  public void weather(User user, Cancellable packet, GameStateChangeReader reader) {
    if (packet.isCancelled()) {
      return;
    }
    GameStateChangeType type = reader.type();
    if (!State.isWeather(type)) {
      return;
    }
    float value = reader.value();
    State state = states.get(user);
    synchronized (state) {
      WeatherEvent weather = state.weather(type, value);
      if (weather != null) {
        emitter.accept(user, weather);
      }
    }
  }

  @PacketSubscription(priority = ListenerPriority.MONITOR, prioritySlot = PrioritySlot.EXTERNAL, packetsOut = UPDATE_TIME)
  public void time(User user, Cancellable packet, TimeUpdateReader reader) {
    if (packet.isCancelled()) {
      return;
    }
    Update update = reader.update();
    State state = states.get(user);
    synchronized (state) {
      emitter.accept(user, state.time(update));
    }
  }

  @PacketSubscription(priority = ListenerPriority.MONITOR, prioritySlot = PrioritySlot.EXTERNAL, packetsOut = RESPAWN)
  public void reset(User user, Cancellable packet) {
    if (packet.isCancelled()) {
      return;
    }
    State state = states.get(user);
    synchronized (state) {
      state.reset();
      // Do not carry the previous packet state through a respawn/dimension change.
      emitter.accept(user, new WeatherEvent());
      emitter.accept(user, new TimeEvent());
    }
  }

  /** Called on the player's owning thread before exposing a new recording sink. */
  void start(User user, EventSink sink, Runnable attachSink) {
    State state = states.get(user);
    synchronized (state) {
      state.weatherSnapshot().accept(sink);
      state.timeSnapshot().accept(sink);
      attachSink.run();
    }
  }

  static final class State {
    private Boolean raining;
    private Boolean thundering;
    private Float rainLevel;
    private Float thunderLevel;
    private TimeEvent time;
    private Long lastGameTime;

    static boolean isWeather(GameStateChangeType type) {
      return type == GameStateChangeType.END_RAIN || type == GameStateChangeType.BEGIN_RAIN
        || type == GameStateChangeType.RAIN_LEVEL_CHANGE || type == GameStateChangeType.THUNDER_LEVEL_CHANGE;
    }

    WeatherEvent weather(GameStateChangeType type, float value) {
      switch (type) {
        case END_RAIN:
          raining = false;
          rainLevel = 1.0F;
          break;
        case BEGIN_RAIN:
          raining = true;
          rainLevel = 0.0F;
          break;
        case RAIN_LEVEL_CHANGE:
          if (!Float.isFinite(value)) {
            return null;
          }
          rainLevel = Math.max(0.0F, Math.min(1.0F, value));
          break;
        case THUNDER_LEVEL_CHANGE:
          if (!Float.isFinite(value)) {
            return null;
          }
          thunderLevel = Math.max(0.0F, Math.min(1.0F, value));
          thundering = null; // The packet carries a strength, not the logical storm flag.
          break;
        default:
          return null;
      }
      return weatherSnapshot();
    }

    TimeEvent time(Update update) {
      TimeEvent event = update.event();
      if (event.clocks() == null) {
        time = event;
      } else {
        Map<String, ClockState> clocks = new LinkedHashMap<>();
        if (time != null && time.clocks() != null) {
          long elapsed = lastGameTime == null ? 0 : Math.max(0, update.gameTime() - lastGameTime);
          for (Map.Entry<String, ClockState> entry : time.clocks().entrySet()) {
            ClockState clock = entry.getValue();
            double advance = clock.partialTick() + elapsed * (double) clock.rate();
            long wholeTicks = (long) Math.floor(advance);
            clocks.put(entry.getKey(), new ClockState(
              clock.time() + wholeTicks, (float) (advance - wholeTicks), clock.rate()
            ));
          }
        }
        clocks.putAll(event.clocks());
        time = new TimeEvent(null, null, clocks);
      }
      lastGameTime = update.gameTime();
      return timeSnapshot();
    }

    WeatherEvent weatherSnapshot() {
      return new WeatherEvent(raining, thundering, rainLevel, thunderLevel);
    }

    TimeEvent timeSnapshot() {
      return time == null ? new TimeEvent() : new TimeEvent(
        time.time(), time.ticking(), time.clocks()
      );
    }

    void reset() {
      raining = null;
      thundering = null;
      rainLevel = null;
      thunderLevel = null;
      time = null;
      lastGameTime = null;
    }
  }
}
