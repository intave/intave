package de.jpx3.intave.module;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.EventProcessor;

public abstract class Module implements EventProcessor {
  protected IntavePlugin plugin;
  private ModuleSettings moduleSettings;

  protected Module() {
  }

  public IntavePlugin plugin() {
    return plugin;
  }

  public void setPlugin(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public ModuleSettings settings() {
    return moduleSettings;
  }

  public void setModuleSettings(ModuleSettings moduleSettings) {
    this.moduleSettings = moduleSettings;
  }

  public void enable() {
  }

  public void disable() {
  }
}
