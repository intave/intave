package de.jpx3.intave.test;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2022
 */

public enum Severity {
  INFO(false),
  WARNING(false),
  ERROR(true);

  private final boolean interrupt;

  Severity(boolean interrupt) {
    this.interrupt = interrupt;
  }

  public boolean mustInterrupt() {
    return interrupt;
  }
}
