package de.jpx3.intave.version;

import de.jpx3.intave.version.MinecraftVersion;

class ProtocolVersionRange implements Comparable<ProtocolVersionRange> {
  private final int from;
  private final int to;
  private final String version;
  private final MinecraftVersion asMinecraftVersion;

  public ProtocolVersionRange(int from, int to, String version) {
    this.from = from;
    this.to = to;
    this.version = version;
    MinecraftVersion asMinecraftVersion;
    try {
      asMinecraftVersion = new MinecraftVersion(version);
    } catch (Exception exception) {
      asMinecraftVersion = new MinecraftVersion("1.0.0");
    }
    this.asMinecraftVersion = asMinecraftVersion;
  }

  public int from() {
    return from;
  }

  public int to() {
    return to;
  }

  public boolean includes(int version) {
    return from <= version && version <= to;
  }

  public String version() {
    return version;
  }

  public MinecraftVersion asMinecraftVersion() {
    return asMinecraftVersion;
  }

  public int compareTo(ProtocolVersionRange other) {
    return to - other.to;
  }

  @Override
  public String toString() {
    return "version " + version + " from " + from + " to " + to;
  }
}
