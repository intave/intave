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

package de.jpx3.intave.block.special;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.world.WorldHeight;

import java.util.Optional;

public final class BedWakeupPositionSearch {
	/**
	 * Finds the same safe stand-up position that the client expects when leaving a bed.
	 * The candidate order and skip behavior mirror Minecraft 1.15's
	 * {@code BedBlock.findStandUpPosition}.
	 */
	public static Optional<Position> findStandUpPosition(
		User user, BlockPosition bedPosition, int positionsToSkip
	) {
		BlockVariant bedVariant = VolatileBlockAccess.variantAccess(user, bedPosition);
		Direction bedDirection = bedVariant.enumProperty(Direction.class, "facing");
		if (bedDirection == null) {
			return Optional.empty();
		}

		int bedX = bedPosition.getBlockX();
		int bedY = bedPosition.getBlockY();
		int bedZ = bedPosition.getBlockZ();

		for (int bedPartOffset = 0; bedPartOffset <= 1; ++bedPartOffset) {
			int minX = bedX - bedDirection.offsetX() * bedPartOffset - 1;
			int minZ = bedZ - bedDirection.offsetZ() * bedPartOffset - 1;
			int maxX = minX + 2;
			int maxZ = minZ + 2;

			for (int x = minX; x <= maxX; ++x) {
				for (int z = minZ; z <= maxZ; ++z) {
					Optional<Position> candidate = standUpPositionAt(user, new BlockPosition(x, bedY, z));
					if (candidate.isPresent()) {
						if (positionsToSkip <= 0) {
							return candidate;
						}
						--positionsToSkip;
					}
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<Position> standUpPositionAt(User user, BlockPosition candidatePosition) {
		BlockCache blockCache = user.blockCache();
		int candidateX = candidatePosition.getBlockX();
		int candidateY = candidatePosition.getBlockY();
		int candidateZ = candidatePosition.getBlockZ();
		BlockShape candidateShape = blockCache.collisionShapeAt(candidateX, candidateY, candidateZ);
		if (!candidateShape.isEmpty()
			&& candidateShape.max(Direction.Axis.Y_AXIS) - candidateY > 0.4375D) {
			return Optional.empty();
		}

		int groundY = candidateY;
		BlockShape groundShape = candidateShape;
		while (groundY >= WorldHeight.LOWER_WORLD_LIMIT
			&& candidateY - groundY <= 2
			&& groundShape.isEmpty()) {
			groundShape = blockCache.collisionShapeAt(candidateX, --groundY, candidateZ);
		}
		if (groundShape.isEmpty()) {
			return Optional.empty();
		}

		double standingY = groundShape.max(Direction.Axis.Y_AXIS) + 2.0E-7D;
		if (candidateY - standingY > 2.0D) {
			return Optional.empty();
		}

		Position standingPosition = new Position(candidateX + 0.5D, standingY, candidateZ + 0.5D);
		MovementMetadata movement = user.meta().movement();
		BoundingBox standingBox = Pose.STANDING.boundingBoxOf(
			user, movement,
			standingPosition.getX(), standingPosition.getY(), standingPosition.getZ()
		);
		return Collision.nonePresent(user, movement, standingBox)
			? Optional.of(standingPosition)
			: Optional.empty();
	}
}
