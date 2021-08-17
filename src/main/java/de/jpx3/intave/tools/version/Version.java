package de.jpx3.intave.tools.version;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Version {
  private final String version;
  private final long release;
  private final Status typeClassifier;

  public Version(String version, long release, Status typeClassifier) {
    this.version = version;
    this.release = release;
    this.typeClassifier = typeClassifier;
  }

  public String version() {
    return version;
  }

  public long release() {
    return release;
  }

  public Status typeClassifier() {
    return typeClassifier;
  }

  public boolean outdated() {
    return typeClassifier == Status.OUTDATED;
  }

  public enum Status {
    OUTDATED("OUTDATED"),
    LATEST("LATEST"),
    STABLE("STABLE"),
    DISABLED("DISABLED"),
    INVALID("");

    private final static Map<String, Status> map = new HashMap<>();
    private final String typeName;

    static {
      for (Status value : values()) {
        map.put(value.typeName(), value);
      }
    }

    public static Status fromName(String name) {
      Status statusLookup = map.get(name.toUpperCase(Locale.ROOT));
      return statusLookup == null ? Status.INVALID : statusLookup;
    }

    Status(String typeName) {
      this.typeName = typeName;
    }

    public String typeName() {
      return typeName;
    }
  }
}
