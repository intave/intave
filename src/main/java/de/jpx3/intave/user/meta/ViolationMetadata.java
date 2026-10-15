/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.user.meta;

import com.google.common.collect.Maps;

import java.util.Map;

public final class ViolationMetadata {
  public double physicsOffset;
  public double physicsVL;
  public double physicsInsignificantBufferVL;
  public double physicsVelocityVL;
  public double physicsVehicleVL;
  public double physicsInvalidMovementsInRow;

  public long lastMovementDebugRequest;

  public double backtrackVL;
  public long lastBacktrackVLChange;
  public long lastBacktrackHitCancelRequest;

  public double wrappedNoSlowdownVL;

  public int detectionCounter;
  public long detectionCounterReset;

  public long lastBlockPlaceDenyRequest;

  public int facingFailedCounter = 0;

  public Map<String, Map<String, Double>> violationLevel = Maps.newConcurrentMap();
  public Map<String, Map<String, Double>> violationLevelGainedCounter = Maps.newConcurrentMap();
  public Map<String, Map<String, Long>> lastViolationLevelGainedCounterReset = Maps.newConcurrentMap();
}
