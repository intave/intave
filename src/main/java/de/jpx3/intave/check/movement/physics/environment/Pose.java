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

package de.jpx3.intave.check.movement.physics.environment;

import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;

import java.util.Map;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_13;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_9;

public enum Pose {
  STANDING,
  FALL_FLYING,
  SWIMMING,
  SLEEPING,
  CROUCHING,

  ;

  private static final Map<Pose, HitboxSize> SIZE_BY_POSE = ImmutableMap.<Pose, HitboxSize>builder()
    .put(STANDING, HitboxSize.playerDefault())
    .put(SLEEPING, HitboxSize.of(0.2f, 0.2f))
    .put(FALL_FLYING, HitboxSize.of(0.6f, 0.6f))
    .put(SWIMMING, HitboxSize.of(0.6f, 0.6f))
    .build();

  public static final Map<Pose, HitboxSize> AT_LEAST_1_8_POSE = ImmutableMap.<Pose, HitboxSize>builder()
    .putAll(SIZE_BY_POSE)
    .put(CROUCHING, HitboxSize.of(0.6f, 1.8f))
    .build();

  public static final Map<Pose, HitboxSize> AT_LEAST_1_9_POSE = ImmutableMap.<Pose, HitboxSize>builder()
    .putAll(SIZE_BY_POSE)
    .put(CROUCHING, HitboxSize.of(0.6f, 1.65f))
    .build();

  public static final Map<Pose, HitboxSize> AT_LEAST_1_13_POSE = ImmutableMap.<Pose, HitboxSize>builder()
    .putAll(SIZE_BY_POSE)
    .put(CROUCHING, HitboxSize.of(0.6f, 1.5f))
    .build();

  public static Map<Pose, HitboxSize> poseSizesByVersion(int version) {
    if (version >= VER_1_13) {
      return Pose.AT_LEAST_1_13_POSE;
    } else if (version >= VER_1_9) {
      return Pose.AT_LEAST_1_9_POSE;
    } else {
      return Pose.AT_LEAST_1_8_POSE;
    }
  }

  public BoundingBox boundingBoxOf(
    User user, SimulationEnvironment environment
  ) {
    return boundingBoxOf(
      user, environment,
      environment.positionX(), environment.positionY(), environment.positionZ()
    );
  }

  public BoundingBox boundingBoxOf(
    User user, SimulationEnvironment environment,
    double x, double y, double z
  ) {
    float halfWidth = width(user, environment) / 2.0F;
    float height = height(user, environment);
    return new BoundingBox(
      x - (double) halfWidth, y, z - (double) halfWidth,
      x + (double) halfWidth, y + (double) height, z + (double) halfWidth
    );
  }

  public float width(User user, SimulationEnvironment environment) {
    Simulator simulator = environment.simulator();
    if (simulator == Simulators.BOAT) {
      return 1.375F;
    }
    if (simulator == Simulators.HORSE && environment.vehicle() != null) {
      return environment.vehicle().typeData().size().width();
    }
    return size(user).width();
  }

  public float height(User user, SimulationEnvironment environment) {
    Simulator simulator = environment.simulator();
    if (simulator == Simulators.BOAT) {
      return 0.5625F;
    }
    if (simulator == Simulators.HORSE && environment.vehicle() != null) {
      return environment.vehicle().typeData().size().height();
    }
    return size(user).height();
  }

  private HitboxSize size(User user) {
    return user.sizeOf(this);
  }
}
