package de.jpx3.intave.command.stages;

import de.jpx3.intave.command.CommandStage;

public class ExternalDebugStage extends CommandStage {
  protected ExternalDebugStage() {
    super(BaseStage.singletonInstance(), "debug");
  }
}
