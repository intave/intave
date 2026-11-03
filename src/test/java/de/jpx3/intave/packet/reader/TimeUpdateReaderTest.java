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

package de.jpx3.intave.packet.reader;

import ac.intave.samples.event.TimeEvent;
import de.jpx3.intave.packet.plib.reader.TimeUpdateReader;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class TimeUpdateReaderTest {
  @Test
  void readsLegacyTwoLongLayout() {
    TimeUpdateReader.Update update = decode(new LegacyTime(100, -18000));
    TimeEvent event = update.event();
    assertEquals(100L, update.gameTime());
    assertEquals(18000L, event.time());
    assertFalse(event.ticking());
    assertNull(event.clocks());
    assertTrue(decode(new LegacyTime(100, 0)).event().ticking());
    assertEquals(1L, decode(new LegacyTime(100, -1)).event().time());
  }

  @Test
  void readsExplicitTickingFlagWithoutInterpretingTheSign() {
    TimeUpdateReader.Update update = decode(new ModernTime(200, -6000, true));
    TimeEvent event = update.event();
    assertEquals(200L, update.gameTime());
    assertEquals(-6000L, event.time());
    assertTrue(event.ticking());
    assertFalse(decode(new ModernTime(200, 0, false)).event().ticking());
  }

  @Test
  void readsNamed26xClocksIncludingAnEmptyUpdate() {
    Map<ClockHolder, ClockState> clocks = Collections.singletonMap(
      new ClockHolder(), new ClockState()
    );
    TimeUpdateReader.Update update = decode(new ClockTime(300, clocks));
    TimeEvent event = update.event();
    assertEquals(300L, update.gameTime());
    assertNull(event.time());
    assertNull(event.ticking());
    assertEquals(18000L, event.clocks().get("test:moon").time());
    assertEquals(0.5F, event.clocks().get("test:moon").partialTick());
    assertEquals(2.5F, event.clocks().get("test:moon").rate());
    assertTrue(decode(new ClockTime(301, Collections.emptyMap())).event().clocks().isEmpty());
  }

  private static TimeUpdateReader.Update decode(Object packet) {
    return TimeUpdateReader.decode(packet);
  }

  private static final class LegacyTime {
    private final long gameTime;
    private final long dayTime;
    LegacyTime(long gameTime, long dayTime) { this.gameTime = gameTime; this.dayTime = dayTime; }
  }

  private static final class ModernTime {
    private final long gameTime;
    private final long dayTime;
    private final boolean tickDayTime;
    ModernTime(long gameTime, long dayTime, boolean tickDayTime) {
      this.gameTime = gameTime; this.dayTime = dayTime; this.tickDayTime = tickDayTime;
    }
  }

  private static final class ClockTime {
    private final long gameTime;
    private final Map<ClockHolder, ClockState> clockUpdates;
    ClockTime(long gameTime, Map<ClockHolder, ClockState> clockUpdates) {
      this.gameTime = gameTime; this.clockUpdates = clockUpdates;
    }
  }

  public static final class ClockHolder {
    public ClockKey key() { return new ClockKey(); }
  }

  public static final class ClockKey {
    public String identifier() { return "test:moon"; }
  }

  public static final class ClockState {
    public long totalTicks() { return 18000; }
    public float partialTick() { return 0.5F; }
    public float rate() { return 2.5F; }
  }
}
