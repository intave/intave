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

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers.EntityPose;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityMetadataReader;
import de.jpx3.intave.packet.view.EntityMetadataView;
import de.jpx3.intave.packet.view.FeedbackHandle;
import de.jpx3.intave.packet.view.PacketEventsEntityMetadataView;
import de.jpx3.intave.packet.view.PacketEventsFeedbackHandle;
import de.jpx3.intave.packet.view.ProtocolLibEntityMetadataView;
import de.jpx3.intave.packet.view.ProtocolLibFeedbackHandle;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.ENTITY_METADATA;

public final class MetadataTracker extends Module {

	@PacketSubscription(
		packetsOut = {
			ENTITY_METADATA
		}
	)
	public void trackMetadata(
		User user, EntityMetadataReader reader,
		PacketEvent event
	) {
		// The view wraps the reader the linker already injected, so the pooling and release
		// semantics of this path are untouched: nothing here acquires or releases a reader.
		handleMetadata(
			user,
			new ProtocolLibEntityMetadataView(event, reader),
			ProtocolLibFeedbackHandle.of(event)
		);
	}

	/**
	 * PacketEvents entry point for the same packet.
	 * <p>
	 * Both values this tracker reads off the packet are served by
	 * {@link PacketEventsEntityMetadataView}: the bed position comes from the same version
	 * dependent metadata index the ProtocolLib reader uses, and the pose is handed back as
	 * ProtocolLib's {@code EnumWrappers.EntityPose}, which is the type {@link #newPoseStatus} tests
	 * for. The tick feedback is requested through the engine neutral {@link FeedbackHandle}; the
	 * PacketEvents handle reports no bundling target, so the transaction is sent unbundled, exactly
	 * as the ProtocolLib path does on every server below 1.19.4.
	 */
	@PacketSubscription(
		engine = Engine.PACKETEVENTS,
		packetsOut = {
			ENTITY_METADATA
		}
	)
	public void trackMetadata(PacketSendEvent event, Player player) {
		if (player == null) {
			return;
		}
		PacketEventsEntityMetadataView view = PacketEventsEntityMetadataView.of(event);
		if (view == null) {
			return;
		}
		handleMetadata(
			UserRepository.userOf(player),
			view,
			PacketEventsFeedbackHandle.of(event)
		);
	}

	/** Engine independent metadata handling; every read goes through the view. */
	private void handleMetadata(User user, EntityMetadataView view, FeedbackHandle handle) {
		if (!view.targetEntityIdIsSameAs(user)) {
			return;
		}
		@Nullable BlockPosition sleepingBedPosition = sleepingBedPosition(view);
		@Nullable Boolean newGliding = newGlidingStatus(user, view);
		@Nullable Pose newPose = newPoseStatus(user, view);

		user.packetTickFeedback(handle, () -> {
			MovementMetadata movement = user.meta().movement();
			movement.sleepingBedPosition = sleepingBedPosition;
			if (sleepingBedPosition != null) {
				Position newPosition = positionFromBedPosition(sleepingBedPosition);
				movement.setPosition(newPosition);
				movement.setVerifiedLastPosition(newPosition, "Bed sleep");
			}
			if (newGliding != null) {
				movement.gliding = newGliding;
			}
			if (newPose != null) {
				movement.setPose(newPose);
			}
		});
	}

	private @Nullable BlockPosition sleepingBedPosition(EntityMetadataView view) {
		return view.bedPosition().orElse(null);
	}

	private Position positionFromBedPosition(BlockPosition bedPosition) {
		return new Position(
			bedPosition.x() + 0.5,
			bedPosition.y() + 0.6875,
			bedPosition.z() + 0.5
		);
	}

	private Boolean newGlidingStatus(User user, EntityMetadataView view) {
		if (!user.meta().protocol().canUseElytra()) {
			return false;
		}
		Object elytraObject = view.fetchRaw(0);
		if (elytraObject == null) {
			return null;
		}
		byte data = (byte) elytraObject;
		return (data & 1 << 7) != 0;
	}

	private @Nullable Pose newPoseStatus(User user, EntityMetadataView view) {
		if (!MinecraftVersions.VER1_14_0.atOrAbove() || !user.meta().protocol().applyModernCollider()
		) {
			return null;
		}
		Object rawPose = view.fetchRaw(6);
		if (rawPose == null) {
			return null;
		}
		try {
			EntityPose entityPose = rawPose instanceof EntityPose
				? (EntityPose) rawPose
				: EntityPose.fromNms(rawPose);
			switch (entityPose) {
				case STANDING:
					return Pose.STANDING;
				case FALL_FLYING:
					return Pose.FALL_FLYING;
				case SLEEPING:
					return Pose.SLEEPING;
				case SWIMMING:
					return Pose.SWIMMING;
				case CROUCHING:
					return Pose.CROUCHING;
				default:
					return null;
			}
		} catch (RuntimeException ignored) {
			return null;
		}
	}
}
