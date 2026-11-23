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

package de.jpx3.intave.module.tracker.player;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packetperfect.OnSend;
import de.jpx3.intave.packet.contract.immutable.EntityMetadataPacket;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;

import java.util.Optional;

import static de.jpx3.intave.packet.ListenerPriority.LATER;

public final class MetadataTracker extends Module {

	@OnSend(priority = LATER)
	public void trackMetadata(
		User user, EntityMetadataPacket packet,
		PacketSendEvent event
	) {
		if (event.isCancelled() || packet.entityId() != user.player().getEntityId()) {
			return;
		}
		@Nullable Optional<BlockPosition> bedUpdate = packet.bedPosition();
		@Nullable Boolean newGliding = newGlidingStatus(user, packet);
		@Nullable Pose newPose = user.meta().protocol().applyModernCollider() ? packet.pose() : null;
		@Nullable Byte livingFlags = packet.livingFlags();
		long itemUseSequence = user.meta().inventory().activeItemUseSequence();

		Modules.feedback().synchronize(user, event, () -> {
			MovementMetadata movement = user.meta().movement();
			InventoryMetadata inventory = user.meta().inventory();
			if (bedUpdate != null) {
				BlockPosition sleepingBedPosition = bedUpdate.orElse(null);
				movement.sleepingBedPosition = sleepingBedPosition;
				if (sleepingBedPosition != null) {
					Material bed = VolatileBlockAccess.typeAccess(user, sleepingBedPosition);
					Position newPosition = positionFromBedPosition(sleepingBedPosition, bed, user.protocolVersion());
					movement.setPosition(newPosition);
					movement.setVerifiedLastPosition(newPosition, "Bed sleep");
				}
			}
			if (newGliding != null) {
				movement.gliding = newGliding;
			}
			if (newPose != null) {
				movement.setPose(newPose);
			}
			if (livingFlags != null) {
				inventory.applyClientItemUseFlags(
					itemUseSequence, (livingFlags & 1) != 0
				);
			}
		});
	}

	static Position positionFromBedPosition(BlockPosition bedPosition, Material bed, int protocol) {
		// 26.3 StrawBedBlock uses the foot's 4/16 outline height; LivingEntity adds 1/8.
		double height = protocol >= ProtocolMetadata.VER_26_3 && bed.name().equals("STRAW_BED")
			? 0.375 : 0.6875;
		return new Position(
			bedPosition.x() + 0.5,
			bedPosition.y() + height,
			bedPosition.z() + 0.5
		);
	}

	private @Nullable Boolean newGlidingStatus(User user, EntityMetadataPacket packet) {
		if (!user.meta().protocol().canUseElytra()) {
			return false;
		}
		Byte flags = packet.entityFlags();
		return flags == null ? null : (flags & 1 << 7) != 0;
	}
}
