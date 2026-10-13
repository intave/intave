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
import de.jpx3.intave.packet.reader.TimeUpdateReader.Update;
import de.jpx3.intave.user.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.share.GameStateChangeType.*;
import static org.junit.jupiter.api.Assertions.*;

final class PlayerEnvironmentRecorderTest {
  @Test
  void emitsUnknownPacketSnapshotsBeforeAttaching() {
    List<Event> events = new ArrayList<>();
    PlayerEnvironmentRecorder recorder = new PlayerEnvironmentRecorder((user, event) -> events.add(event));
    User fallback = (User) Proxy.newProxyInstance(User.class.getClassLoader(), new Class<?>[]{User.class},
      (proxy, method, args) -> {
        if (method.getName().equals("hasPlayer")) return false;
        throw new UnsupportedOperationException(method.getName());
      });
    EventSink sink = new EventSink() {
      @Override public void visitAny(Event event) { events.add(event); }
      @Override public String name() { return "test"; }
    };

    recorder.start(fallback, sink, () -> assertEquals(2, events.size()));

    assertNull(((WeatherEvent) events.get(0)).raining());
    assertNull(((TimeEvent) events.get(1)).time());
  }

  @Test
  void combinesIndependentWeatherPacketsAndPreservesFades() {
    PlayerEnvironmentRecorder.State state = new PlayerEnvironmentRecorder.State();
    WeatherEvent start = state.weather(BEGIN_RAIN, 0);
    assertTrue(start.raining());
    assertEquals(0.0F, start.rainLevel());
    state.weather(RAIN_LEVEL_CHANGE, 0.75F);
    WeatherEvent thunder = state.weather(THUNDER_LEVEL_CHANGE, 0.5F);
    assertTrue(thunder.raining());
    assertEquals(0.75F, thunder.rainLevel());
    assertEquals(0.5F, thunder.thunderLevel());
    assertNull(thunder.thundering());
    WeatherEvent stop = state.weather(END_RAIN, 0);
    assertFalse(stop.raining());
    assertEquals(1.0F, stop.rainLevel());
    assertEquals(0.5F, stop.thunderLevel());
    assertEquals(0.0F, start.rainLevel(), "Previously emitted snapshots must not change");
    assertNull(state.weather(CHANGE_GAME_MODE, 1));
    assertNull(state.weather(RAIN_LEVEL_CHANGE, Float.NaN));
    assertNull(state.weather(THUNDER_LEVEL_CHANGE, Float.POSITIVE_INFINITY));
    assertEquals(1.0F, state.weatherSnapshot().rainLevel());
  }

  @Test
  void mergesPartialClockPacketsWithoutSerializingGameTime() {
    PlayerEnvironmentRecorder.State state = new PlayerEnvironmentRecorder.State();
    TimeEvent first = state.time(new Update(100L, new TimeEvent(null, null,
      Collections.singletonMap("custom:moon", new ClockState(18000, 0.25F, 0.5F)))));
    TimeEvent second = state.time(new Update(103L, new TimeEvent(null, null,
      Collections.singletonMap("minecraft:overworld", new ClockState(6000, 0.5F, 0)))));

    assertEquals(2, second.clocks().size());
    assertEquals(18001, second.clocks().get("custom:moon").time());
    assertEquals(0.75F, second.clocks().get("custom:moon").partialTick());
    assertEquals(6000, second.clocks().get("minecraft:overworld").time());
    assertEquals(1, first.clocks().size());
    assertEquals(18000, first.clocks().get("custom:moon").time());

    TimeEvent emptyUpdate = state.time(new Update(105L,
      new TimeEvent(null, null, Collections.emptyMap())));
    assertEquals(6000, emptyUpdate.clocks().get("minecraft:overworld").time());
    assertEquals(18002, emptyUpdate.clocks().get("custom:moon").time());
  }

  @Test
  void usesLegacyPlayerTimeAndClearsAllPacketStateOnRespawn() {
    PlayerEnvironmentRecorder.State state = new PlayerEnvironmentRecorder.State();
    state.weather(BEGIN_RAIN, 0);
    state.time(new Update(100L, new TimeEvent(18000L, false)));

    assertEquals(18000L, state.timeSnapshot().time());
    assertFalse(state.timeSnapshot().ticking());

    state.reset();
    assertNull(state.weatherSnapshot().raining());
    assertNull(state.timeSnapshot().time());
    assertNull(state.timeSnapshot().ticking());
    assertNull(state.timeSnapshot().clocks());
  }
}
