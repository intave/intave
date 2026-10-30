package de.jpx3.intave.module.nayoro;

import ac.intave.samples.event.Event;
import ac.intave.samples.event.HeaderEvent;
import ac.intave.samples.event.PropertiesEvent;
import ac.intave.samples.serial.JsonWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class PlaybackPropertiesTest {
  @Test
  void preservesStringValuesAndBooleanLookupsAcrossPropertyUpdates() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (JsonWriter writer = new JsonWriter(output)) {
      writer.visitAny(new HeaderEvent());
      writer.visitAny(new PropertiesEvent(Map.of(
        "combatVersion", "MODERN", "reducingDisabled", "true", "threshold", "0.75"
      )));
      writer.visitAny(new PropertiesEvent(Map.of("reducingDisabled", "false")));
    }

    Playback playback = new Playback(new ByteArrayInputStream(output.toByteArray())) {
      @Override
      public void start() {}

      @Override
      public void stop() {}

      @Override
      public long currentTime() {
        return 0;
      }

      @Override
      public String name() {
        return "test";
      }

      @Override
      public void visitAny(Event event) {}
    };
    try {
      playback.nextEvent().accept(playback);
      assertEquals("MODERN", playback.properties().get("combatVersion"));
      assertEquals("0.75", playback.properties().get("threshold"));
      assertTrue(playback.property("reducingDisabled"));
      assertFalse(playback.property("combatVersion"));
      assertFalse(playback.property("missing"));

      playback.nextEvent().accept(playback);
      assertFalse(playback.property("reducingDisabled"));
      assertEquals("MODERN", playback.properties().get("combatVersion"));
      assertNull(playback.nextEvent());
    } finally {
      playback.closeRecording();
    }
  }
}
