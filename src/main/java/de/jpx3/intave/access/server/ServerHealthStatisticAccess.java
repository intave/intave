package de.jpx3.intave.access.server;

import java.util.function.Consumer;

public interface ServerHealthStatisticAccess {
  double tickAverageOver(TimeSpan timeSpan);
  void subscribeToTick(TimeSpan timeSpan, Consumer<Double> average);

  enum TimeSpan {
    LAST_MINUTE,
    LAST_FIVE_MINUTES,
    LAST_TEN_MINUTES
  }
}
