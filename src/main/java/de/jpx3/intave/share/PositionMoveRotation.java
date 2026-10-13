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

package de.jpx3.intave.share;

import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.plib.converter.PosMoveRotConverter;
import io.netty.buffer.ByteBuf;

import java.util.Set;

public final class PositionMoveRotation {
  public static final StreamCodec<ByteBuf, ByteBuf, PositionMoveRotation> STREAM_CODEC = StreamCodec.compound(
    Position.STREAM_CODEC, PositionMoveRotation::position,
    Motion.STREAM_CODEC, PositionMoveRotation::motion,
    Rotation.STREAM_CODEC, PositionMoveRotation::rotation,
    PositionMoveRotation::new
  );
  private final Position position;
  private final Motion motion;
  private final Rotation rotation;

  public PositionMoveRotation(
    Position position, Motion motion, Rotation rotation
  ) {
    this.position = position.mutable();
    this.motion = motion;
    this.rotation = rotation;
  }

	public Position position() {
    return position;
  }

  public Motion motion() {
    return motion;
  }

  public Rotation rotation() {
    return rotation;
  }

  public static PositionMoveRotation firstFrom(
    PacketContainer packet
  ) {
    return packet.getModifier()
      .withType(
        PosMoveRotConverter.nativePositionMoveRotClass,
        PosMoveRotConverter.INSTANCE
      ).read(0);
  }

  // If a teleport flag is set, we use the old value and add the change to it
  // Otherwise the change is absolute, and we use it as is
  public static PositionMoveRotation merge(
    PositionMoveRotation current,
    PositionMoveRotation change,
    Set<Relative> relativeSet
  ) {
    Position keepFromOldPosition = current.position().filtered(relativeSet);
    Position newPosition = keepFromOldPosition.add(change.position()).mutable();
    Rotation keepFromOldRotation = current.rotation().filtered(relativeSet);
    Rotation newRotation = keepFromOldRotation.add(change.rotation());
    Motion keepFromOldMotion = current.motion();
    if (relativeSet.contains(Relative.ROTATE_DELTA)) {
      Rotation rot = current.rotation().subtract(newRotation);
      keepFromOldMotion = keepFromOldMotion.rotated(rot);
    }
    Motion newMotion = keepFromOldMotion.filtered(relativeSet).add(change.motion());
    return new PositionMoveRotation(newPosition, newMotion, newRotation);
  }

  public void applyTo(SimulationEnvironment environment) {
    environment.setPosition(position);
    environment.setLastPosition(position);
    environment.setVerifiedLastPosition(position, "verified");
    environment.setBoundingBox(BoundingBox.fromPosition(environment.user(), environment, position));
    environment.setBaseMotion(motion);
    // A teleport replaces the motion from the preceding tick. Otherwise the
    // previous-post-tick brancher can restore that motion on the next move.
    environment.clearPostTickMotionCandidates();
    environment.setRotation(rotation);
  }

  public PositionMoveRotation merge(PositionMoveRotation change, Set<Relative> relativeSet) {
    return merge(this, change, relativeSet);
  }

  @Override
  public String toString() {
    return "PositionMoveRotation{" +
      "position=" + position +
      ", motion=" + motion +
      ", rotation=" + rotation +
      '}';
  }

  @Override
  public int hashCode() {
    int result = position.hashCode();
    result = 31 * result + motion.hashCode();
    result = 31 * result + rotation.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof PositionMoveRotation) {
      PositionMoveRotation other = (PositionMoveRotation) obj;
      return this.position.equals(other.position)
        && this.motion.equals(other.motion)
        && this.rotation.equals(other.rotation);
    }
    return false;
  }

  public static PositionMoveRotation of(
    Position position, Motion motion, Rotation rotation
  ) {
    return new PositionMoveRotation(position, motion, rotation);
  }

  public static PositionMoveRotation withoutMotion(
    Position position, Rotation rotation
  ) {
    return new PositionMoveRotation(position, Motion.newEmpty(), rotation);
  }

  public static PositionMoveRotation noMotionRelativePosition(Position position) {
    return new PositionMoveRotation(position, Motion.newEmpty(), Rotation.zero());
  }

  public static PositionMoveRotation withoutRotation(Position position, Motion motion) {
    return new PositionMoveRotation(position, motion, Rotation.zero());
  }
}
