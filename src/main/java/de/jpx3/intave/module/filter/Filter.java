package de.jpx3.intave.module.filter;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.EventProcessor;

public class Filter implements EventProcessor {
  private final String name;
  private final boolean enabled;

  public Filter(String name) {
    this.name = name;
    this.enabled = IntavePlugin.singletonInstance().configurationService().configuration().getBoolean("filter." + name);
  }

  protected boolean enabled() {
    return enabled;
  }
}
