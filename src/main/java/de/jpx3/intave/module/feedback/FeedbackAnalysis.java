package de.jpx3.intave.module.feedback;

import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.storage.FeedbackAnalysisStorage;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
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

  public void receivedTransaction(User user, FeedbackRequest<?> request) {
    FeedbackAnalysisMeta meta = metaOf(user);
    FeedbackCategory category = FeedbackCategory.fromFeedbackOptions(request.options());
    Map<FeedbackCategory, LatencyAnalysis> latencyAnalysisMap = meta.latencyAnalysisMap;

    LatencyAnalysis analysis = latencyAnalysisMap.get(category);
    LatencyAnalysis combatNearAnalysis = latencyAnalysisMap.get(ENTITY_NEAR);

    if (category == ENTITY_NEAR ||
      System.currentTimeMillis() - combatNearAnalysis.lastEntry() > 1500) {
      analysis.addLatency(request.passedTime());
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
    long entityNearLatency = entityNear.averageLatency();
//    long entityNearCombatLatency = meta.latencyAnalysisMap.getOrDefault(ENTITY_NEAR_COMBAT, general).averageLatency();
//    entityNearLatency = (entityNearLatency + entityNearCombatLatency) / 2;
    //    user.player().sendMessage(discrepancy + "ms discrepancy, general: " + generalLatency + ", near: " + entityNearLatency);
    if (entityNear.count < 100) {
      return -1;
    } else if (general.count < 100) {
      return -2;
    }/* else if (entityFar.count < 100) {
      return -3;
    }*/
    return entityNearLatency - generalLatency;
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

    public long lastEntry() {
      return lastEntry;
    }
  }

  public enum FeedbackCategory {
    GENERAL,
    ENTITY_FAR,
    ENTITY_NEAR,
    ENTITY_NEAR_COMBAT

    ;

    public static FeedbackCategory fromFeedbackOptions(int options) {
      if (FeedbackOptions.matches(TRACER_ENTITY_FAR, options)) {
        return ENTITY_FAR;
      }
      if (FeedbackOptions.matches(TRACER_ENTITY_NEAR, options)) {
        return ENTITY_NEAR;
      }
      if (FeedbackOptions.matches(TRACER_ENTITY_NEAR_COMBAT, options)) {
        return ENTITY_NEAR_COMBAT;
      }
      return GENERAL;
    }
  }
}
