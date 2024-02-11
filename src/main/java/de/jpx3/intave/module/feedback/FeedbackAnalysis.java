package de.jpx3.intave.module.feedback;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.storage.FeedbackAnalysisStorage;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.jpx3.intave.module.feedback.FeedbackAnalysis.FeedbackCategory.*;
import static de.jpx3.intave.module.feedback.FeedbackOptions.*;

public final class FeedbackAnalysis extends Module {
  @BukkitEventSubscription
  public void on(PlayerJoinEvent join) {
    User user = UserRepository.userOf(join.getPlayer());
    user.onStorageReady(storage -> {
      FeedbackAnalysisStorage theStorage = user.storageOf(FeedbackAnalysisStorage.class);
      FeedbackAnalysisMeta meta = metaOf(user);
      for (int i = 0; i < values().length; i++) {
        if (theStorage.accumulatedLatencies() != null && theStorage.counts() != null) {
          meta.latencyAnalysisMap.get(values()[i]).set(theStorage.accumulatedLatencies()[i], theStorage.counts()[i]);
        }
      }
    });
  }

  @BukkitEventSubscription
  public void on(PlayerQuitEvent quit) {
    User user = UserRepository.userOf(quit.getPlayer());
    user.onStorageReady(storage -> {
      FeedbackAnalysisStorage theStorage = user.storageOf(FeedbackAnalysisStorage.class);
      FeedbackCategory[] values = values();
      theStorage.setAccumulatedLatencies(new long[values.length]);
      theStorage.setCounts(new long[values.length]);
      FeedbackAnalysisMeta meta = metaOf(user);
      for (int i = 0; i < values.length; i++) {
        theStorage.accumulatedLatencies()[i] = meta.latencyAnalysisMap.get(values[i]).accumulatedLatency;
        theStorage.counts()[i] = meta.latencyAnalysisMap.get(values[i]).count;
      }
    });
  }

  public void sentTransaction(User user, FeedbackRequest<?> request) {
    FeedbackAnalysisMeta meta = metaOf(user);
    // unimportant
  }

  private UserLocal<File> latencyAnalysisFile = UserLocal.withInitial(user -> new File(IntavePlugin.singletonInstance().dataFolder(), user.id() + "-latency.csv"));

  public void receivedTransaction(User user, FeedbackRequest<?> request) {
    FeedbackAnalysisMeta meta = metaOf(user);
    FeedbackCategory category = fromFeedbackOptions(request.options());
    Map<FeedbackCategory, LatencyAnalysis> latencyAnalysisMap = meta.latencyAnalysisMap;

    LatencyAnalysis analysis = latencyAnalysisMap.get(category);
    LatencyAnalysis combatNearAnalysis = latencyAnalysisMap.get(ENTITY_NEAR);

    long passedTime = request.passedTime();
    meta.fullLatencyAnalysis.addLatency(passedTime);

    if (category == ENTITY_NEAR && user.meta().attack().recentlyAttacked(1000)) {
      double probability = meta.fullLatencyAnalysis.biasedProbabilityOf(passedTime, 300);
      // 1 in 50_000
      if (probability < 0.00005) {
        meta.suspiciousLatencies.add(new FeedbackAnalysisMeta.LatencyInfo(passedTime));
      }
    }

    /*
    boolean attacked = FeedbackOptions.matches(TRACER_ENTITY_NEAR, request.options());
    boolean exclude = !attacked && user.meta().attack().recentlyAttacked(500);

    BackgroundExecutors.executeWhenever(() -> {
      // append to file, [latency, combatNear]
      File file = latencyAnalysisFile.get(user);
      if (!file.exists()) {
        try {
          file.createNewFile();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        // write header
        FileWriter writer = null;
        try {
          writer = new FileWriter(file);
          writer.write("latency,combatNear\n");
        } catch (IOException e) {
          throw new RuntimeException(e);
        } finally {
          if (writer != null) {
            try {
              writer.close();
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        }
      }
      if (!exclude) {
        FileWriter writer = null;
        try {
          writer = new FileWriter(file, true);
          writer.write(passedTime + "," + (attacked ? 1 : 0) + "\n");
        } catch (IOException e) {
          throw new RuntimeException(e);
        } finally {
          if (writer != null) {
            try {
              writer.close();
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        }
      }
    });*/

    if (category == ENTITY_NEAR ||
      System.currentTimeMillis() - combatNearAnalysis.lastEntry() > 1500) {
      analysis.addLatency(passedTime);
    }
  }

  public long entityLatencyDiscrepancy(User user) {
    FeedbackAnalysisMeta meta = metaOf(user);
    LatencyAnalysis general = meta.latencyAnalysisMap.get(GENERAL);
    LatencyAnalysis entityNear = meta.latencyAnalysisMap.getOrDefault(ENTITY_NEAR, general);
    LatencyAnalysis entityFar = meta.latencyAnalysisMap.getOrDefault(ENTITY_FAR, general);
    if (general == null) {
      return 0;
    }
    long generalLatency = general.averageLatency();
    long entityFarLatency = entityFar.averageLatency();
    if (entityFarLatency > 0) {
      generalLatency = (generalLatency + entityFarLatency) / 2;
    }
    if (generalLatency > 200) {
      return -3;
    }
    long entityNearLatency = entityNear.averageLatency();
//    long entityNearCombatLatency = meta.latencyAnalysisMap.getOrDefault(ENTITY_NEAR_COMBAT, general).averageLatency();
//    entityNearLatency = (entityNearLatency + entityNearCombatLatency) / 2;
    if (entityNear.count < 100) {
      return -1;
    } else if (general.count < 100) {
      return -2;
    }/* else if (entityFar.count < 100) {
      return -3;
    }*/
    return entityNearLatency - generalLatency;
  }

  public long entityNearLatency(User user) {
    return metaOf(user).latencyAnalysisMap.get(ENTITY_NEAR).averageLatency();
  }

  public long generalLatency(User user) {
    return metaOf(user).latencyAnalysisMap.get(GENERAL).averageLatency();
  }

  public List<FeedbackAnalysisMeta.LatencyInfo> suspiciousLatencies(User user) {
    return metaOf(user).suspiciousLatencies;
  }

  public double meanLatency(User user) {
    return metaOf(user).fullLatencyAnalysis.mean();
  }

  public double stdDev(User user) {
    return metaOf(user).fullLatencyAnalysis.biasedStdDev(300);
  }

  public double latencyProbability(User user, long latency) {
    return metaOf(user).fullLatencyAnalysis.biasedProbabilityOf(latency, 300);
  }

  public FeedbackAnalysisMeta metaOf(User user) {
    return (FeedbackAnalysisMeta) user.checkMetadata(FeedbackAnalysisMeta.class);
  }

  public static class FeedbackAnalysisMeta extends CheckCustomMetadata {
    private final Map<FeedbackCategory, LatencyAnalysis> latencyAnalysisMap = new HashMap<>();
    {
      latencyAnalysisMap.put(GENERAL, new LatencyAnalysis(500));
      latencyAnalysisMap.put(ENTITY_FAR, new LatencyAnalysis(500));
      latencyAnalysisMap.put(ENTITY_NEAR, new LatencyAnalysis(250));
      latencyAnalysisMap.put(ENTITY_NEAR_COMBAT, new LatencyAnalysis(250));
    }
    private final LongLatencyAnalysis fullLatencyAnalysis = new LongLatencyAnalysis();
    private final List<LatencyInfo> suspiciousLatencies = new ArrayList<>(50);
    public static class LatencyInfo {
      private final long latency;
      private final long time = System.currentTimeMillis();

      public LatencyInfo(long latency) {
        this.latency = latency;
      }

      public long latency() {
        return latency;
      }

      public long issued() {
        return time;
      }
    }
  }

  public static class LatencyAnalysis {
    private long accumulatedLatency;
    private long count;
    private long lastEntry;
    private final long size;

    public LatencyAnalysis(long size) {
      this.size = size;
    }

    public void set(long accumulatedLatency, long count) {
      this.accumulatedLatency = accumulatedLatency;
      this.count = count;
    }

    public void addLatency(long latency) {
      latency = Math.min(latency, 1000);
      accumulatedLatency += latency;
      count++;
      if (count > size) {
        accumulatedLatency /= 2;
        count /= 2;
      }
      lastEntry = System.currentTimeMillis();
    }

    public long averageLatency() {
      if (count == 0) {
        return 0;
      }
      return accumulatedLatency / count;
    }

    public double mean() {
      return (double) accumulatedLatency / count;
    }

    public long lastEntry() {
      return lastEntry;
    }
  }

  public static class LongLatencyAnalysis {
    private static final int MAX_LATENCY = 1000;
    private static final int LATENCY_BUCKETS = 100;
    private final long[] latencyOccurrences = new long[LATENCY_BUCKETS + 1];
    private long size = 0;

    public boolean addLatency(long latency) {
      if (latency > MAX_LATENCY) {
        return false;
      }
      latencyOccurrences[(int) asDiscrete(latency)]++;
      size++;
      if (size > 10_000) {
        // divide all by 2
        for (int i = 0; i < LATENCY_BUCKETS; i++) {
          latencyOccurrences[i] /= 2;
        }
        size /= 2;
      }
      return true;
    }

    private long asDiscrete(long latency) {
      return Math.min(latency, MAX_LATENCY) / (MAX_LATENCY / LATENCY_BUCKETS);
    }

    public double mean() {
      long sum = 0;
      int scalingFactor = MAX_LATENCY / LATENCY_BUCKETS;
      for (int i = 0; i < LATENCY_BUCKETS; i++) {
        sum += latencyOccurrences[i] * i * scalingFactor;
      }
      return (double) sum / size;
    }

    public long stdDev() {
      return stdDev(mean());
    }

    public long stdDev(double mean) {
      double sum = 0;
      int scalingFactor = MAX_LATENCY / LATENCY_BUCKETS;
      for (int i = 0; i < LATENCY_BUCKETS; i++) {
        sum += Math.pow(i*scalingFactor - mean, 2) * latencyOccurrences[i];
      }
      return (long) Math.sqrt(sum / size);
    }

    public double biasedStdDev(double requiredDistance) {
      return biasedStdDev(mean(), requiredDistance);
    }

    public double biasedStdDev(double mean, double requiredDistance) {
      double sum = 0;
      int scalingFactor = MAX_LATENCY / LATENCY_BUCKETS;
      for (int i = 0; i < LATENCY_BUCKETS; i++) {
        double dist = Math.abs(i*scalingFactor - mean);
        double weight = Math.exp(-dist / requiredDistance);
        sum += Math.pow(dist, 2) * latencyOccurrences[i] * weight;
      }
      return Math.sqrt(sum / size);
    }

    public double likelihoodOf(long latency) {
      return latencyOccurrences[(int) asDiscrete(latency)] / (double) size;
    }

    public double probabilityOf(long latency) {
      double mean = mean();
      double stdDev = stdDev(mean);
      return Math.exp(-Math.pow(latency - mean, 2) / (2 * Math.pow(stdDev, 2))) / (stdDev * Math.sqrt(2 * Math.PI));
    }

    public double biasedProbabilityOf(long latency, double biasDistance) {
      double mean = mean();
      if (latency < mean) {
        return 100;
      }
      double stdDev = biasedStdDev(mean, biasDistance);
      if (latency < mean + stdDev) {
        return 100;
      }
      return Math.exp(-Math.pow(latency - mean, 2) / (2 * Math.pow(stdDev, 2))) / (stdDev * Math.sqrt(2 * Math.PI));
    }
  }

  public enum FeedbackCategory {
    GENERAL,
    ENTITY_FAR,
    ENTITY_NEAR,
    ENTITY_NEAR_COMBAT

    ;

    public static FeedbackCategory fromFeedbackOptions(int options) {
      if (matches(TRACER_ENTITY_FAR, options)) {
        return ENTITY_FAR;
      }
      if (matches(TRACER_ENTITY_NEAR, options)) {
        return ENTITY_NEAR;
      }
      if (matches(TRACER_ENTITY_NEAR_COMBAT, options)) {
        return ENTITY_NEAR_COMBAT;
      }
      return GENERAL;
    }
  }
}
