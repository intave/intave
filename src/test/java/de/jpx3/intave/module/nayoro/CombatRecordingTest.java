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

import ac.intave.samples.event.ClickEvent;
import ac.intave.samples.event.Event;
import ac.intave.samples.event.HeaderEvent;
import ac.intave.samples.event.PlayerInitEvent;
import ac.intave.samples.event.PropertiesEvent;
import ac.intave.samples.serial.JsonReader;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class CombatRecordingTest {
  @AfterEach
  void resetVersion() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
  }

  @Test
  void recordsInitialCombatModeAndTransitionsBeforeAffectedEvents() throws Exception {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    Map<String, Boolean> properties = new HashMap<>();
    properties.put("reducingDisabled", true);
    properties.put("combatVersionKnown", false);
    properties.put("modernCombat", false);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    RecordEventSink sink = new RecordEventSink(environment(properties), output);
    sink.visitAny(new ClickEvent());
    properties.put("combatVersionKnown", true);
    properties.put("modernCombat", true);
    sink.visitAny(new ClickEvent());
    sink.visitAny(new ClickEvent()); // Unchanged state must not add another properties event.
    properties.put("modernCombat", false);
    sink.visitAny(new ClickEvent());
    properties.put("combatVersionKnown", false);
    sink.visitAny(new ClickEvent()); // A respawn invalidates the previous evidence.
    sink.close();

    try (JsonReader reader = new JsonReader(new ByteArrayInputStream(output.toByteArray()))) {
      assertInstanceOf(HeaderEvent.class, reader.nextEvent());
      assertInstanceOf(PlayerInitEvent.class, reader.nextEvent());
      assertProperties(reader.nextEvent(), false, false);
      assertInstanceOf(ClickEvent.class, reader.nextEvent());
      assertProperties(reader.nextEvent(), true, true);
      assertEquals(0, assertInstanceOf(ClickEvent.class, reader.nextEvent()).offset());
      assertInstanceOf(ClickEvent.class, reader.nextEvent());
      assertProperties(reader.nextEvent(), true, false);
      assertEquals(0, assertInstanceOf(ClickEvent.class, reader.nextEvent()).offset());
      assertProperties(reader.nextEvent(), false, false);
      assertInstanceOf(ClickEvent.class, reader.nextEvent());
      assertNull(reader.nextEvent());
    }
  }

  @Test
  void recordingStartedDuringLegacyCombatIncludesTheCurrentMode() throws Exception {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    Map<String, Boolean> properties = new HashMap<>();
    properties.put("reducingDisabled", true);
    properties.put("combatVersionKnown", true);
    properties.put("modernCombat", false);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    RecordEventSink sink = new RecordEventSink(environment(properties), output);
    sink.close();
    try (JsonReader reader = new JsonReader(new ByteArrayInputStream(output.toByteArray()))) {
      assertInstanceOf(HeaderEvent.class, reader.nextEvent());
      assertInstanceOf(PlayerInitEvent.class, reader.nextEvent());
      assertProperties(reader.nextEvent(), true, false);
      assertNull(reader.nextEvent());
    }
  }

  private static void assertProperties(Event event, boolean known, boolean modern) {
    Map<String, Boolean> properties = assertInstanceOf(PropertiesEvent.class, event).properties();
    assertEquals(known, properties.get("combatVersionKnown"));
    assertEquals(modern, properties.get("modernCombat"));
    assertEquals(true, properties.get("reducingDisabled"));
  }

  private static Environment environment(Map<String, Boolean> properties) {
    PlayerContainer player = (PlayerContainer) Proxy.newProxyInstance(
      PlayerContainer.class.getClassLoader(), new Class<?>[] {PlayerContainer.class},
      (proxy, method, arguments) -> switch (method.getName()) {
        case "name" -> "Combat test";
        case "uuid" -> new UUID(0, 1);
        case "id" -> 1;
        case "version" -> 769;
        case "position" -> new Position(0, 64, 0);
        case "rotation" -> new Rotation(0, 0);
        case "applyIfUserPresent" -> null;
        default -> throw new UnsupportedOperationException(method.getName());
      }
    );
    return (Environment) Proxy.newProxyInstance(
      Environment.class.getClassLoader(), new Class<?>[] {Environment.class},
      (proxy, method, arguments) -> switch (method.getName()) {
        case "mainPlayer" -> player;
        case "properties" -> new HashMap<>(properties);
        default -> throw new UnsupportedOperationException(method.getName());
      }
    );
  }
}
