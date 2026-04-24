package de.jpx3.intave.module;

import de.jpx3.intave.adapter.PacketEventsAdapter;

import java.util.Arrays;

final class Requirements {
  public static Requirement none() {
    return new NoRequirement();
  }

  public static Requirement packetEvents() {
    return requiresPlugin("packetevents").and(PacketEventsAdapter::packetEventsAvailable);
  }

  public static Requirement intaveEnabled() {
    return requiresPlugin("Intave");
  }

  public static Requirement requiresPlugin(String plugin) {
    return new PluginRequirement(plugin);
  }

  public static Requirement requiresPlugins(String... plugins) {
    return new PluginRequirement(plugins);
  }

  public static Requirement mergeAnd(Requirement... requirements) {
    return () -> Arrays.stream(requirements).allMatch(Requirement::fulfilled);
  }

  public static Requirement mergeOr(Requirement... requirements) {
    return () -> Arrays.stream(requirements).anyMatch(Requirement::fulfilled);
  }
}
