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

import java.util.EnumSet;
import java.util.OptionalInt;
import java.util.Set;

public final class Teleport {
	private final long uniqueId;
	private final OptionalInt id;
	private final PositionMoveRotation change;
	private final Set<Relative> relativeSet;
	private volatile boolean allowed = false;
	private volatile boolean accepted = false;
	private Boolean additiveMotionPacket;
	private Boolean simulatedOnGround;

	public Teleport(
		long uniqueId, OptionalInt id,
		PositionMoveRotation change,
		Set<Relative> relativeSet
	) {
		this.uniqueId = uniqueId;
		this.id = id;
		this.change = change;
		this.relativeSet = relativeSet;
	}

	public long uniqueId() {
		return uniqueId;
	}

	public OptionalInt id() {
		return id;
	}

	public boolean matchesId(int id) {
		return !this.id.isPresent() || this.id.getAsInt() == id;
	}

	public PositionMoveRotation change() {
		return change;
	}

	public Set<Relative> relativeSet() {
		return relativeSet;
	}

	public void copyRelPosFlagsToRelDeltaFlags() {
		if (relativeSet.contains(Relative.X)) {
			relativeSet.add(Relative.DELTA_X);
		}
		if (relativeSet.contains(Relative.Y)) {
			relativeSet.add(Relative.DELTA_Y);
		}
		if (relativeSet.contains(Relative.Z)) {
			relativeSet.add(Relative.DELTA_Z);
		}
	}

	public boolean matches(
		Position lastPosition, Rotation lastRotation,
		Position sentPosition, Rotation sentRotation,
		double positionTolerance, float rotationTolerance
	) {
		if (!isAllowed() || wasAccepted()) {
			return false;
		}
		PositionMoveRotation expected = expectedPositionMoveRotation(lastPosition, lastRotation);
		return expected.position().isWithin(sentPosition, positionTolerance)
			&& expected.rotation().isWithin(sentRotation, rotationTolerance);
	}

	public PositionMoveRotation expectedPositionMoveRotation(
		Position lastVerifiedPlayerPosition, Rotation lastVerifiedPlayerRotation
	) {
		return expectedPositionMoveRotation(lastVerifiedPlayerPosition, lastVerifiedPlayerRotation, Motion.newEmpty());
	}

	public PositionMoveRotation expectedPositionMoveRotation(
		Position lastVerifiedPlayerPosition, Rotation lastVerifiedPlayerRotation, Motion previousMotion
	) {
		return new PositionMoveRotation(
			lastVerifiedPlayerPosition, previousMotion, lastVerifiedPlayerRotation
		).merge(change, relativeSet);
	}

	public void allow() {
		this.allowed = true;
	}

	public void disallow() {
		this.allowed = false;
	}

	public boolean isAllowed() {
		return allowed;
	}

	public void accept() {
		this.accepted = true;
	}

	public boolean wasAccepted() {
		return accepted;
	}

	public Boolean simulatedOnGround() {
		return simulatedOnGround;
	}

	public void setSimulatedOnGround(Boolean onGround) {
		this.simulatedOnGround = onGround;
	}

	public Boolean additiveMotionPacket() {
		return additiveMotionPacket;
	}

	public void setAdditiveMotionPacket(Boolean additiveMotionPacket) {
		this.additiveMotionPacket = additiveMotionPacket;
	}

	@Override
	public String toString() {
		return "Teleport{" +
			"uniqueId=" + uniqueId +
			", id=" + id +
			", change=" + change +
			", relativeSet=" + relativeSet +
			", allowed=" + allowed +
			", accepted=" + accepted +
			'}';
	}

	@Override
	public int hashCode() {
		int result = 17;
		result = 31 * result + Long.hashCode(uniqueId);
		result = 31 * result + id.hashCode();
		result = 31 * result + change.hashCode();
		result = 31 * result + relativeSet.hashCode();
		result = 31 * result + Boolean.hashCode(allowed);
		result = 31 * result + Boolean.hashCode(accepted);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Teleport) {
			Teleport other = (Teleport) obj;
			return this.uniqueId == other.uniqueId &&
				this.id.equals(other.id) &&
				this.change.equals(other.change) &&
				this.relativeSet.equals(other.relativeSet) &&
				this.allowed == other.allowed &&
				this.accepted == other.accepted;
		}
		return false;
	}

	public static Teleport of(
		long sequence, OptionalInt id, PositionMoveRotation change,
		Set<Relative> relativeSet, Boolean onGround, boolean nativeMotion
	) {
		Set<Relative> flags = EnumSet.noneOf(Relative.class);
		flags.addAll(relativeSet);
		Boolean additive = null;
		Motion motion = change.motion().copy();
		if (!nativeMotion && !motion.isZero()) {
			additive = isAdditiveMotion(flags);
			motion = encodeLegacyMotion(motion, additive);
			flags = effectiveMotionFlags(flags, additive);
		}
		Teleport teleport = new Teleport(
			sequence, id, new PositionMoveRotation(
			Position.mutableCopy(change.position()),
			motion, new Rotation(change.rotation().yaw(), change.rotation().pitch())
		), flags
		);
		teleport.setAdditiveMotionPacket(additive);
		teleport.setSimulatedOnGround(onGround);
		return teleport;
	}

	static boolean isAdditiveMotion(Set<Relative> flags) {
		return flags.contains(Relative.DELTA_X) ||
			flags.contains(Relative.DELTA_Y) ||
			flags.contains(Relative.DELTA_Z);
	}

	static Motion encodeLegacyMotion(Motion motion, boolean additive) {
		return new Motion(
			encodeLegacyAxis(motion.motionX(), additive),
			encodeLegacyAxis(motion.motionY(), additive),
			encodeLegacyAxis(motion.motionZ(), additive)
		);
	}

	static double encodeLegacyAxis(double value, boolean additive) {
		return additive ?
			(double) (float) value :
			(int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value * 8000.0D)) / 8000.0D;
	}

	static Set<Relative> effectiveMotionFlags(Set<Relative> positionFlags, boolean additive) {
		Set<Relative> flags = EnumSet.noneOf(Relative.class);
		flags.addAll(positionFlags);
		flags.removeAll(Relative.RELATIVE_MOTION);
		if (additive) {
			if (flags.contains(Relative.X)) {
				flags.add(Relative.DELTA_X);
			}
			if (flags.contains(Relative.Y)) {
				flags.add(Relative.DELTA_Y);
			}
			if (flags.contains(Relative.Z)) {
				flags.add(Relative.DELTA_Z);
			}
		}
		return flags;
	}
}
