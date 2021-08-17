package de.jpx3.intave.user.meta;

import com.google.common.collect.Maps;
import de.jpx3.intave.annotate.Relocate;

import java.util.Map;

@Relocate
public final class ViolationMetadata {
  public double physicsVL;
  public double physicsVelocityVL;
  public double physicsInvalidMovementsInRow;
  public volatile boolean isInActiveTeleportBundle;

  public int detectionCounter;
  public long detectionCounterReset;

  public Map<String, Map<String, Double>> violationLevel = Maps.newConcurrentMap();
  public Map<String, Map<String, Double>> violationLevelGainedCounter = Maps.newConcurrentMap();
  public Map<String, Map<String, Long>> lastViolationLevelGainedCounterReset = Maps.newConcurrentMap();
}