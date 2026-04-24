package de.jpx3.intave.version;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

public class MinecraftVersion implements Comparable<MinecraftVersion> {
  public static final MinecraftVersion AQUATIC_UPDATE = new MinecraftVersion("1.13");

  private final String version;
  private final int[] parts;

  public MinecraftVersion(String version) {
    this.version = normalize(version);
    this.parts = parse(this.version);
  }

  public static MinecraftVersion getCurrentVersion() {
    try {
      return new MinecraftVersion(String.valueOf(Bukkit.class.getMethod("getMinecraftVersion").invoke(null)));
    } catch (Throwable ignored) {
      String bukkitVersion = Bukkit.getBukkitVersion();
      int dash = bukkitVersion.indexOf('-');
      return new MinecraftVersion(dash == -1 ? bukkitVersion : bukkitVersion.substring(0, dash));
    }
  }

  public int getMajor() {
    return part(0);
  }

  public int getMinor() {
    return part(1);
  }

  public int getBuild() {
    return part(2);
  }

  public boolean isAtLeast(MinecraftVersion other) {
    return compareTo(other) >= 0;
  }

  public boolean atOrAbove() {
    return getCurrentVersion().compareTo(this) >= 0;
  }

  public String getVersion() {
    return version;
  }

  @Override
  public int compareTo(MinecraftVersion other) {
    int length = Math.max(parts.length, other.parts.length);
    for (int i = 0; i < length; i++) {
      int left = i < parts.length ? parts[i] : 0;
      int right = i < other.parts.length ? other.parts[i] : 0;
      if (left != right) {
        return left - right;
      }
    }
    return 0;
  }

  @Override
  public String toString() {
    return version;
  }

  private static String normalize(String input) {
    if (input == null || input.trim().isEmpty()) {
      return "0";
    }
    String trimmed = input.trim();
    int space = trimmed.indexOf(' ');
    if (space > 0) {
      trimmed = trimmed.substring(0, space);
    }
    return trimmed.replaceAll("[^0-9.].*$", "");
  }

  private static int[] parse(String input) {
    String[] split = input.split("\\.");
    List<Integer> parsed = new ArrayList<>();
    for (String value : split) {
      if (value.isEmpty()) {
        continue;
      }
      try {
        parsed.add(Integer.parseInt(value));
      } catch (NumberFormatException ignored) {
        parsed.add(0);
      }
    }
    int[] result = new int[parsed.size()];
    for (int i = 0; i < parsed.size(); i++) {
      result[i] = parsed.get(i);
    }
    return result;
  }

  private int part(int index) {
    return index >= 0 && index < parts.length ? parts[index] : 0;
  }
}
