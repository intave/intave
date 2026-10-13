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

package de.jpx3.intave.packet.contract;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.share.BlockPosition;

import java.util.Optional;
import java.util.OptionalInt;

public interface EntityMetadataPacket extends Packet, PrimaryEntityAddressed {

	@Nullable
	Byte entityFlags();

	@Nullable
	Byte livingFlags();

	@Nullable
	Float health();

	@Nullable
	Pose pose();

	@Nullable
	Optional<BlockPosition> bedPosition();

	@Nullable
	OptionalInt attachedEntityId();

	@Nullable
	Boolean baby(EntityTypeData entityType);

	default boolean hasEntityFlags() {
		return entityFlags() != null;
	}

	default boolean hasLivingFlags() {
		return livingFlags() != null;
	}

	default boolean hasHealth() {
		return health() != null;
	}

	default boolean hasPose() {
		return pose() != null;
	}

	default boolean updatesBedPosition() {
		return bedPosition() != null;
	}

	default boolean clearsBedPosition() {
		Optional<BlockPosition> position = bedPosition();
		return position != null && !position.isPresent();
	}

	default boolean updatesAttachedEntityId() {
		return attachedEntityId() != null;
	}

	default boolean clearsAttachedEntityId() {
		OptionalInt entityId = attachedEntityId();
		return entityId != null && !entityId.isPresent();
	}

	default boolean hasBabyUpdate(EntityTypeData entityType) {
		return baby(entityType) != null;
	}

	@Override
	default int entityCount() {
		OptionalInt attachedEntityId = attachedEntityId();
		return attachedEntityId != null && attachedEntityId.isPresent() ? 2 : 1;
	}

	@Override
	default int entityId(int index) {
		if (index == 0) {
			return entityId();
		}
		OptionalInt attachedEntityId = attachedEntityId();
		if (index == 1 && attachedEntityId != null && attachedEntityId.isPresent()) {
			return attachedEntityId.getAsInt();
		}
		throw new IndexOutOfBoundsException("Entity index: " + index);
	}
}
