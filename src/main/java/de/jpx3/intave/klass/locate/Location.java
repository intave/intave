package de.jpx3.intave.klass.locate;

import de.jpx3.intave.adapter.MinecraftVersion;

abstract class Location {
  private final String key;
  private final VersionMatcher versionMatcher;

  public Location(String key, VersionMatcher versionMatcher) {
    this.key = key;
    this.versionMatcher = versionMatcher;
  }

  public String key() {
    return key;
  }

  public boolean matchesVersion(
    MinecraftVersion version
  ) {
    return versionMatcher.matches(version);
  }

  public VersionMatcher versionMatcher() {
    return versionMatcher;
  }
}
