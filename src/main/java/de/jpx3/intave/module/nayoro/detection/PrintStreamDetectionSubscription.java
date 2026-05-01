package de.jpx3.intave.module.nayoro.detection;

import de.jpx3.intave.module.mitigate.AttackNerfStrategy;

import java.io.PrintStream;

public final class PrintStreamDetectionSubscription implements DetectionSubscription {
  private final PrintStream printStream;

  public PrintStreamDetectionSubscription(PrintStream printStream) {
    this.printStream = printStream;
  }

  @Override
  public void onDebug(String message) {
    printStream.println("[intave/nayoro/debug] " + message);
  }

  @Override
  public void onNerf(AttackNerfStrategy strategy, String originCode) {
    printStream.println("[intave/nayoro/nerf] " + strategy.name() + ": " + originCode);
  }
}
