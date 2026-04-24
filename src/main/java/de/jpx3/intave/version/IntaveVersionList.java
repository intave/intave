package de.jpx3.intave.version;

import de.jpx3.intave.IntavePlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class IntaveVersionList {
  private final List<IntaveVersion> content = new ArrayList<>();
  private final Map<String, IntaveVersion> contentLookup = new HashMap<>();

  public IntaveVersionList() {
  }

  public void setup() {
    register(new IntaveVersion(IntavePlugin.version(), System.currentTimeMillis(), IntaveVersion.Status.LATEST));
  }

  public IntaveVersion current() {
    return versionInformation(IntavePlugin.version());
  }

  public IntaveVersion past(int versions) {
    return content.get(indexOf(current()) - versions);
  }

  private int indexOf(IntaveVersion version) {
    return content.indexOf(version);
  }

  public IntaveVersion versionInformation(String version) {
    return contentLookup.get(version.toLowerCase(Locale.ROOT));
  }

  public List<IntaveVersion> content() {
    return content;
  }

  private void register(IntaveVersion version) {
    content.add(version);
    contentLookup.put(version.version().toLowerCase(Locale.ROOT), version);
  }
}
