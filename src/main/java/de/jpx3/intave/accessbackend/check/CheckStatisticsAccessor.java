package de.jpx3.intave.accessbackend.check;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.check.CheckStatisticsAccess;
import de.jpx3.intave.access.check.UnknownCheckException;
import de.jpx3.intave.detect.IntaveCheck;

import java.util.Map;

public final class CheckStatisticsAccessor {
  private final IntavePlugin plugin;

  public CheckStatisticsAccessor(
    IntavePlugin plugin
  ) {
    this.plugin = plugin;
  }

  private final static Map<String, CheckStatisticsAccess> statisticAccessCache = Maps.newConcurrentMap();

  public synchronized CheckStatisticsAccess statisticsOf(String name) {
    Preconditions.checkNotNull(name);
    return statisticAccessCache.computeIfAbsent(name, x -> newStatisticsMirror(tryGetCheck(name)));
  }

  private IntaveCheck tryGetCheck(String name) {
    try {
      return plugin.checkService().searchCheck(name);
    } catch (NullPointerException nullptr) {
      throw new UnknownCheckException("Could not find check " + name);
    }
  }

  private CheckStatisticsAccess newStatisticsMirror(IntaveCheck intaveCheck) {
    return new CheckStatisticsAccess() {
      @Override
      public final long totalViolations() {
        return intaveCheck.baseStatistics().totalViolations();
      }

      @Override
      public long totalPasses() {
        return intaveCheck.baseStatistics().totalPasses();
      }

      @Override
      public long totalProcesses() {
        return intaveCheck.baseStatistics().totalProcessed();
      }

      @Override
      public long totalFails() {
        return intaveCheck.baseStatistics().totalFails();
      }
    };
  }
}
