package de.jpx3.intave.klass.locate;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.IntaveLogger;

public interface VersionMatcher {
  boolean matches(MinecraftVersion version);

  default VersionMatcher and(VersionMatcher other) {
    return version -> this.matches(version) && other.matches(version);
  }

  default VersionMatcher or(VersionMatcher other) {
    return version -> this.matches(version) || other.matches(version);
  }

  // "*" -> any()
  // "1.8" -> exact(1.8.0)
  // "[1.8-1.16]" -> between(1.8.0, 1.16.0)
  // "[1.16-26.1]" -> between(1.16.0, 26.1.0)
  static VersionMatcher fromText(
    String input
  ) {
    input = input.trim();
    if (input.equals("*")) {
      return any();
    }
    String[] split = input.split("-");
    if (split.length == 1) {
      if (!split[0].contains(".")) {
        throw new IllegalStateException("Invalid version format: " + input);
      }
      return exact(new MinecraftVersion(split[0]));
    } else if (split.length == 2) {
      boolean inBraces = input.startsWith("[") && input.endsWith("]");
      if (!inBraces) {
        throw new IllegalArgumentException("Invalid version range format: " + input);
      }
      if (!split[0].contains(".") || !split[1].contains(".")) {
        throw new IllegalStateException("Invalid version format: " + input);
      }
      MinecraftVersion from = new MinecraftVersion(split[0].substring(1));
      MinecraftVersion to = new MinecraftVersion(split[1].substring(0, split[1].length() - 1));
      if (from.getMajor() < 26 && from.getMajor() > 1) {
        IntaveLogger.logger().warn("Version range with major version " + from.getMajor() + " is likely a mistake: " + input);
      }
      if (to.getMajor() < 26 && to.getMajor() > 1) {
        IntaveLogger.logger().warn("Version range with major version " + to.getMajor() + " is likely a mistake: " + input);
      }
      return between(from, to);
    } else {
      throw new IllegalArgumentException("Invalid version matcher: " + input);
    }
  }

  static VersionMatcher between(
    MinecraftVersion from,
    MinecraftVersion to
  ) {
    return (ver) -> ver.compareTo(from) >= 0 && ver.compareTo(to) <= 0;
  }

  static VersionMatcher exact(MinecraftVersion target) {
    return (ver) -> ver.equals(target);
  }

  static VersionMatcher any() {
    return (ver) -> true;
  }
}
