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

package de.jpx3.intave.check.movement.physics.update;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.meta.MovementMetadata;

import java.util.concurrent.locks.Lock;

public final class MotionUpdate extends TickAmbiguousUpdate {
  private final Motion motion;
  private final boolean additive;
  private volatile CausalConstraint constraint;
  private volatile boolean corrected;
  private long sentBeforeTeleport = Long.MAX_VALUE;

  private MotionUpdate(Motion motion, boolean additive, CausalConstraint constraint) {
    this.motion = motion.copy();
    this.additive = additive;
    this.constraint = constraint;
  }

  @Override
  public void applyTo(SimulationEnvironment environment) {
    environment.setBaseMotion(additive
      ? environment.mutableBaseMotionCopy().add(motion)
      : motion);
  }

  public void activate(MovementMetadata movement) {
    Lock lock = movement.user().meta().connection().feedbackAndTeleportLock;
    lock.lock();
    try {
      if (corrected) return;
      constraint = CausalConstraint.openEnded(movement.currentTick(), movement.newSequenceNumber());
      movement.queueTickAmbiguousUpdate(this);
    } finally {
      lock.unlock();
    }
  }

  public void corrected() {
    corrected = true;
  }

  public long sentBeforeTeleport() {
    return sentBeforeTeleport;
  }

  public void setSentBeforeTeleport(long sequence) {
    sentBeforeTeleport = sequence;
  }

  @Override
  public CausalConstraint constraint() {
    return constraint;
  }

  public void setRunNotAfter(long notAfter) {
    constraint = constraint.notAfter(notAfter);
  }

  public void canNotRunAfterThisTick(SimulationEnvironment environment) {
    setRunNotAfter(environment.currentTick());
  }

  @Override
  public boolean expired(SimulationEnvironment environment) {
    return corrected || environment.activeSequence() >= constraint.sequenceNumber();
  }

  @Override
  public boolean possible(SimulationEnvironment environment) {
    return !expired(environment) && environment.currentTick() >= constraint.notBefore();
  }

  @Override
  public boolean canBePostponed(SimulationEnvironment environment) {
    return environment.currentTick() < constraint.notAfter();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof MotionUpdate)) return false;
    MotionUpdate other = (MotionUpdate) obj;
    return additive == other.additive
      && motion.equals(other.motion)
      && constraint.equals(other.constraint);
  }

  @Override
  public int hashCode() {
    return 31 * motion.hashCode() + constraint.hashCode() + (additive ? 1 : 0);
  }

  @Override
  public String toString() {
    return "MotionUpdate{motion=" + motion + ", additive=" + additive + ", at=" + constraint + "}";
  }

  public static MotionUpdate replacement(Motion motion, MovementMetadata movement) {
    return new MotionUpdate(motion, false, CausalConstraint.openEnded(
      movement.currentTick(), movement.newSequenceNumber()));
  }

  public static MotionUpdate pendingReplacement(Motion motion) {
    return pending(motion, false);
  }

  public static MotionUpdate pendingAddition(Motion motion) {
    return pending(motion, true);
  }

  private static MotionUpdate pending(Motion motion, boolean additive) {
    return new MotionUpdate(motion, additive,
      CausalConstraint.openEnded(Long.MAX_VALUE, Long.MAX_VALUE));
  }
}
