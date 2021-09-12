package de.jpx3.intave.accessbackend.server;

import com.google.common.collect.Maps;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.server.ServerHealthStatisticAccess;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.metric.ServerHealth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ServerStatisticAccessor {
  private final IntavePlugin plugin;
  private ServerHealthStatisticAccess statisticAccess;
  private final Map<ServerHealthStatisticAccess.TimeSpan, List<Consumer<Double>>> subscriptions = Maps.newConcurrentMap();
  private int schedulerId;

  public ServerStatisticAccessor(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public synchronized ServerHealthStatisticAccess serverStatisticAccess() {
    if (statisticAccess == null) {
      statisticAccess = newServerStatisticAccess();
      loadScheduler();
    }
    return statisticAccess;
  }

  private void loadScheduler() {
    schedulerId = plugin.getServer().getScheduler().scheduleAsyncRepeatingTask(plugin, () -> {
      subscriptions.forEach((timeSpan, doubleConsumers) -> {
        double tickAverage = tickAverageOf(timeSpan);
        for (Consumer<Double> doubleConsumer : doubleConsumers) {
          doubleConsumer.accept(tickAverage);
        }
      });
    },20,20 * 5);
    ShutdownTasks.add(this::shutdownScheduler);
  }

  public void shutdownScheduler() {
    if (schedulerId > 0 && statisticAccess != null) {
      plugin.getServer().getScheduler().cancelTask(schedulerId);
    }
  }

  private double tickAverageOf(ServerHealthStatisticAccess.TimeSpan span) {
    return ServerHealth.recentTickAverage()[indexOf(span)];
  }

  private int indexOf(ServerHealthStatisticAccess.TimeSpan timeSpan) {
    return timeSpan.ordinal();
  }

  private ServerHealthStatisticAccess newServerStatisticAccess() {
    return new ServerHealthStatisticAccess() {
      @Override
      public double tickAverageOver(TimeSpan timeSpan) {
        return tickAverageOf(timeSpan);
      }

      @Override
      public void subscribeToTick(TimeSpan timeSpan, Consumer<Double> averagePush) {
        subscriptions.computeIfAbsent(timeSpan, x -> new ArrayList<>()).add(averagePush);
      }
    };
  }
}
