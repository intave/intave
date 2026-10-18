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

package de.jpx3.intave.check.movement.physics.branch;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.check.movement.physics.config.TraceImmutableMovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.check.movement.physics.update.TickAmbiguousUpdate;
import de.jpx3.intave.user.User;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class MovementSearchInput {
  private final User user;
  private final Simulator simulator;
  private final SimulationEnvironment environment;
  private final boolean detectNoSlowdown;
  private final @Nullable TraceImmutableMovementConfiguration tracedAfterTickMovementConfig;
  private Map<UnaryOperator<SimulationEnvironment>, SimulationEnvironment> modifiedEnvironmentCache;
  private @Nullable List<TickAmbiguousUpdate> sortedPossibleTickAmbiguousUpdates;
  private boolean itemUseDebugUpdated;

  private MovementSearchInput(User user, Simulator simulator, SimulationEnvironment environment, boolean detectNoSlowdown, TraceImmutableMovementConfiguration tracedAfterTickMovementConfig) {
    this.user = user;
	  this.simulator = simulator;
	  this.environment = environment;
    this.detectNoSlowdown = detectNoSlowdown;
	  this.tracedAfterTickMovementConfig = tracedAfterTickMovementConfig;
  }

  public static MovementSearchInput forTick(User user, Simulator simulator, SimulationEnvironment environment, boolean detectNoSlowdown) {
    return new MovementSearchInput(user, simulator, environment, detectNoSlowdown, null);
  }

  public static MovementSearchInput forAfterTick(User user, Simulator simulator, SimulationEnvironment environment, boolean detectNoSlowdown, TraceImmutableMovementConfiguration tracedAfterTickMovementConfig) {
    return new MovementSearchInput(
      user, simulator, environment, detectNoSlowdown, tracedAfterTickMovementConfig
    );
  }

  User user() {
    return user;
  }

  Simulator simulator() {
    return simulator;
  }

  SimulationEnvironment environment() {
    return environment.immutableView();
  }

  SimulationEnvironment modifiedImmutableEnvironment(
    UnaryOperator<SimulationEnvironment> modifier
  ) {
    if (modifiedEnvironmentCache == null) {
      modifiedEnvironmentCache = new IdentityHashMap<>();
    }
    SimulationEnvironment cached = modifiedEnvironmentCache.get(modifier);
    if (cached != null) {
      return cached;
    }
    SimulationEnvironment modified = modifier.apply(environment.mutableView()).immutableView();
    modifiedEnvironmentCache.put(modifier, modified);
    return modified;
  }

  boolean detectNoSlowdown() {
    return detectNoSlowdown;
  }

  List<TickAmbiguousUpdate> sortedPossibleTickAmbiguousUpdates() {
    if (sortedPossibleTickAmbiguousUpdates == null) {
      List<TickAmbiguousUpdate> updates = environment().possibleTickAmbiguousUpdates();
      if (updates.size() > 1) {
        Collections.sort(updates);
      }
      sortedPossibleTickAmbiguousUpdates = updates;
    }
    return sortedPossibleTickAmbiguousUpdates;
  }

  boolean shouldUpdateItemUseDebug() {
    if (itemUseDebugUpdated) {
      return false;
    }
    itemUseDebugUpdated = true;
    return true;
  }

  boolean jumpingBranchNecessary() {
    if (tracedAfterTickMovementConfig == null) {
      return true;
    }
    return tracedAfterTickMovementConfig.requiredJumpingState();
  }

  boolean sprintingBranchNecessary() {
    if (tracedAfterTickMovementConfig == null) {
      return true;
    }
    return tracedAfterTickMovementConfig.requiredSprintingState();
  }

  boolean actualMotionBranchNecessary() {
    if (tracedAfterTickMovementConfig == null) {
      return false;
    }
    return tracedAfterTickMovementConfig.requiredActualMotionOverride();
  }

  boolean blockInsideCheckBranchNecessary() {
    return tracedAfterTickMovementConfig != null
      && tracedAfterTickMovementConfig.requiredBlockInsideCheckVersion();
  }
}
