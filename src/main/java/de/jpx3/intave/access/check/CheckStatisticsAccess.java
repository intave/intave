package de.jpx3.intave.access.check;

/**
 * Lookup check-specific statistics
 */
public interface CheckStatisticsAccess {
  long totalProcesses();
  long totalPasses();
  long totalViolations();
  long totalFails();
}
