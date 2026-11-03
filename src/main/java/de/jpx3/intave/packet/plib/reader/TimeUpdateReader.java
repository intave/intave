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

package de.jpx3.intave.packet.plib.reader;

import ac.intave.samples.event.TimeEvent;
import ac.intave.samples.share.ClockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Decodes the player-specific state from every supported outgoing time packet. */
public final class TimeUpdateReader extends AbstractPacketReader {
  private static final ClassValue<Layout> LAYOUTS = new ClassValue<Layout>() {
    @Override
    protected Layout computeValue(Class<?> type) {
      return new Layout(type);
    }
  };

  public Update update() {
    return decode(packet().getHandle());
  }

  static Update decode(Object packet) {
    Layout layout = LAYOUTS.get(packet.getClass());
    try {
      long gameTime = layout.gameTime.getLong(packet);
      if (layout.dayTime != null) {
        long dayTime = layout.dayTime.getLong(packet);
        TimeEvent event = layout.ticking == null
          ? TimeEvent.fromLegacyPacket(dayTime)
          : new TimeEvent(dayTime, layout.ticking.getBoolean(packet));
        return new Update(gameTime, event);
      }
      return new Update(
        gameTime,
        new TimeEvent(null, null, decodeClocks((Map<?, ?>) layout.clocks.get(packet)))
      );
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("Unable to read time packet", exception);
    }
  }

  private static Map<String, ClockState> decodeClocks(Map<?, ?> updates) {
    Map<String, ClockState> clocks = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : updates.entrySet()) {
      Object key = invoke(entry.getKey(), "key");
      String name = invoke(key, "identifier").toString();
      Object state = entry.getValue();
      clocks.put(name, new ClockState(
        ((Number) invoke(state, "totalTicks")).longValue(),
        ((Number) invoke(state, "partialTick")).floatValue(),
        ((Number) invoke(state, "rate")).floatValue()
      ));
    }
    return clocks;
  }

  /**
   * The game time is retained only while merging partial 26.x packet updates. It
   * is intentionally absent from the serialized sample event.
   */
  public static final class Update {
    private final long gameTime;
    private final TimeEvent event;

    public Update(long gameTime, TimeEvent event) {
      this.gameTime = gameTime;
      this.event = event;
    }

    public long gameTime() {
      return gameTime;
    }

    public TimeEvent event() {
      return event;
    }
  }

  private static final class Layout {
    private final Field gameTime;
    private final Field dayTime;
    private Field ticking;
    private Field clocks;

    private Layout(Class<?> type) {
      List<Field> longs = new ArrayList<>();
      for (Field field : type.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        if (field.getType() == long.class) {
          field.setAccessible(true);
          longs.add(field);
        } else if (field.getType() == boolean.class) {
          field.setAccessible(true);
          ticking = field;
        } else if (Map.class.isAssignableFrom(field.getType())) {
          field.setAccessible(true);
          clocks = field;
        }
      }
      if (longs.isEmpty() || (longs.size() == 1 && clocks == null)) {
        throw new IllegalStateException("Unsupported time packet layout: " + type.getName());
      }
      gameTime = longs.get(0);
      dayTime = longs.size() > 1 ? longs.get(1) : null;
    }
  }

  // These named accessors only exist on unobfuscated 26.x servers. Keep all NMS
  // references behind the reader so loading it remains safe on Java 8 servers.
  private static Object invoke(Object target, String name) {
    try {
      Method method = target.getClass().getMethod(name);
      return method.invoke(target);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Unable to read named clock field " + name, exception);
    }
  }
}
