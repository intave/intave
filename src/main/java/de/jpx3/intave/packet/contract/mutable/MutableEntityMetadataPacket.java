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

package de.jpx3.intave.packet.contract.mutable;

import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.packet.contract.EntityMetadataPacket;
import de.jpx3.intave.share.BlockPosition;

import java.util.Optional;
import java.util.OptionalInt;

public interface MutableEntityMetadataPacket extends EntityMetadataPacket, MutablePrimaryEntityAddressed {

	void setEntityFlags(byte flags);

	void setLivingFlags(byte flags);

	void setHealth(float health);

	void setPose(Pose pose);

	void setBedPosition(Optional<BlockPosition> position);

	void setAttachedEntityId(OptionalInt entityId);

	void setBaby(EntityTypeData entityType, boolean baby);

	default void setBedPosition(BlockPosition position) {
		setBedPosition(Optional.of(position));
	}

	default void clearBedPosition() {
		setBedPosition(Optional.empty());
	}

	default void setAttachedEntityId(int entityId) {
		setAttachedEntityId(OptionalInt.of(entityId));
	}

	default void clearAttachedEntityId() {
		setAttachedEntityId(OptionalInt.empty());
	}

	@Override
	default void setEntityId(int index, int entityId) {
		if (index == 0) {
			setEntityId(entityId);
			return;
		}
		OptionalInt attachedEntityId = attachedEntityId();
		if (index == 1 && attachedEntityId != null && attachedEntityId.isPresent()) {
			setAttachedEntityId(entityId);
			return;
		}
		throw new IndexOutOfBoundsException("Entity index: " + index);
	}
}
