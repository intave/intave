package de.jpx3.intave.check;

/**
 * {@link CheckStatistics} hold four {@code long} values, that define the accuracy of a check.
 * A {@code long} value for <i>total processes</i>, a {@code long} value for <i>total violations</i>,
 * a {@code long} value for <i>total passes</i> and a {@code long} value for <i>total failures</i>
 * <br>
 * A check can pass or fail, and when it fails it can choose to
 * execute a violation or not. Fails describe the internal violations of a check, rather than actual violations.
 * The total amount of processes is defined as all passes, fails and violations combined.
 */
public final class CheckStatistics {
  private long totalProcessed;
  private long totalViolations;
  private long totalPassed;
  private long totalFails;

  public void increasePasses() {
    totalPassed++;
  }

  public void increaseFails() {
    totalFails++;
  }

  public void increaseViolations() {
    totalViolations++;
  }

  public void increaseTotal() {
    totalProcessed++;
  }

  public void reset() {
    totalProcessed = 0;
    totalViolations = 0;
    totalPassed = 0;
    totalFails = 0;
  }

  /**
   * Retrieve the amount of total violations
   *
   * @return the amount of total violations
   */
  public long totalViolations() {
    return totalViolations;
  }

  public long totalProcessed() {
    return totalProcessed;
  }

  public long totalPasses() {
    return totalPassed;
  }

  public long totalFails() {
    return totalFails;
  }
}
