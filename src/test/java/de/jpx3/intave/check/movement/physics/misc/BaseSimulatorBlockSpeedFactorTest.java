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

package de.jpx3.intave.check.movement.physics.misc;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.block.physics.BlockPhysics;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.BlockCacheEntry;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static de.jpx3.intave.user.meta.ProtocolMetadata.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class BaseSimulatorBlockSpeedFactorTest {
	private static final double EPSILON = 1.0E-12;
	private static final Position POSITION = Position.of(0.5, 50.0, 0.5);
	private static final Rotation ROTATION = Rotation.zero();
	private static final Motion INPUT_MOTION = new Motion(0.8, 0.0, -0.6);
	private static final MovementConfiguration CONFIGURATION = MovementConfiguration.blank();
	private static final BlockCache AIR = new MockFullBlockStaticPlane();
	private static final BlockCache HONEY = new MaterialPlaneBlockCache(49, Material.HONEY_BLOCK);
	private static final BlockCache SOUL_SAND = new MaterialPlaneBlockCache(49, Material.SOUL_SAND);
	private static final Position LEGACY_SOUL_SAND_POSITION = Position.of(0.5, 49.875, 0.5);

	@BeforeEach
	void setUp() {
		MinecraftVersion version = new MinecraftVersion("26.1.2");
		MinecraftVersion.setCurrent(version);
		BlockPhysics.setup(version);
	}

	@Test
	void activeFallFlyingBypassesHoneyBeforePoseCatchesUp() {
		Motion air = simulateAfterTick(VER_26_1_1, AIR, Pose.STANDING, true, false, false);
		Motion honey = simulateAfterTick(VER_26_1_1, HONEY, Pose.STANDING, true, false, false);

		assertMotionEquals(air, honey);
	}

	@Test
	void staleFallFlyingPoseDoesNotBypassHoneyAfterFlagClears() {
		Motion air = simulateAfterTick(VER_26_1_1, AIR, Pose.FALL_FLYING, false, false, false);
		Motion honey = simulateAfterTick(VER_26_1_1, HONEY, Pose.FALL_FLYING, false, false, false);

		assertHorizontalScale(air, honey, 0.4F);
	}

	@Test
	void abilityFlyingBypassesHoneySpeedFactor() {
		Motion air = simulateAfterTick(VER_26_1_1, AIR, Pose.STANDING, false, true, true);
		Motion honey = simulateAfterTick(VER_26_1_1, HONEY, Pose.STANDING, false, true, true);

		assertMotionEquals(air, honey);
	}

	@Test
	void merelyAllowingFlightDoesNotBypassHoneySpeedFactor() {
		Motion air = simulateAfterTick(VER_26_1_1, AIR, Pose.STANDING, false, false, true);
		Motion honey = simulateAfterTick(VER_26_1_1, HONEY, Pose.STANDING, false, false, true);

		assertHorizontalScale(air, honey, 0.4F);
	}

	@Test
	void ordinaryMovementUsesGenericHoneySpeedFactorFrom115() {
		Motion air = simulateAfterTick(VER_1_15, AIR, Pose.STANDING, false, false, false);
		Motion honey = simulateAfterTick(VER_1_15, HONEY, Pose.STANDING, false, false, false);

		assertHorizontalScale(air, honey, 0.4F);
	}

	@Test
	void pre115DoesNotUseGenericHoneySpeedFactor() {
		Motion air = simulateAfterTick(VER_1_14, AIR, Pose.STANDING, false, false, false);
		Motion honey = simulateAfterTick(VER_1_14, HONEY, Pose.STANDING, false, false, false);

		assertMotionEquals(air, honey);
	}

	@Test
	void pre115SoulSandUsesOnlyLegacyCollisionSlowdown() {
		Motion air = simulateAfterTick(
			VER_1_14, AIR, LEGACY_SOUL_SAND_POSITION,
			Pose.STANDING, false, false, false
		);
		Motion soulSand = simulateAfterTick(
			VER_1_14, SOUL_SAND, LEGACY_SOUL_SAND_POSITION,
			Pose.STANDING, false, false, false
		);

		assertHorizontalScale(air, soulSand, 0.4D);
	}

	@Test
	void movementEfficiencyInterpolatesBlockSlowdownFrom121() {
		Motion air = simulateAfterTickWithMovementEfficiency(VER_1_21, AIR, 0.5D);
		Motion honey = simulateAfterTickWithMovementEfficiency(VER_1_21, HONEY, 0.5D);

		float expectedFactor = 0.4F + 0.5F * (1.0F - 0.4F);
		assertHorizontalScale(air, honey, expectedFactor);
	}

	@Test
	void pre121ClientIgnoresMovementEfficiencyAttribute() {
		Motion air = simulateAfterTickWithMovementEfficiency(VER_1_20_5, AIR, 0.5D);
		Motion honey = simulateAfterTickWithMovementEfficiency(VER_1_20_5, HONEY, 0.5D);

		assertHorizontalScale(air, honey, 0.4F);
	}

	@Test
	void waterMovementEfficiencyInterpolatesPostMoveSlowdownFrom121() {
		Motion normal = simulateAfterTickWithWaterMovementEfficiency(VER_1_21, 0.0D);
		Motion efficient = simulateAfterTickWithWaterMovementEfficiency(VER_1_21, 1.0D);
		float normalMultiplier = 0.8F;
		float efficientMultiplier = normalMultiplier
			+ (0.54600006F - normalMultiplier) * 0.5F;

		double expectedScale = efficientMultiplier / normalMultiplier;
		assertEquals(normal.motionX * expectedScale, efficient.motionX, 1.0E-7D);
		assertEquals(normal.motionY, efficient.motionY, EPSILON);
		assertEquals(normal.motionZ * expectedScale, efficient.motionZ, 1.0E-7D);
	}

	private static Motion simulateAfterTickWithMovementEfficiency(
		int protocolVersion,
		BlockCache blockCache,
		double movementEfficiency
	) {
		return simulateAfterTick(
			protocolVersion, blockCache, POSITION,
			Pose.STANDING, false, false, false,
			movementEfficiency, 0.0D, false
		);
	}

	private static Motion simulateAfterTickWithWaterMovementEfficiency(
		int protocolVersion,
		double waterMovementEfficiency
	) {
		return simulateAfterTick(
			protocolVersion, AIR, POSITION,
			Pose.STANDING, false, false, false,
			0.0D, waterMovementEfficiency, true
		);
	}

	private static Motion simulateAfterTick(
		int protocolVersion,
		BlockCache blockCache,
		Pose pose,
		boolean gliding,
		boolean abilityFlying,
		boolean allowFlying
	) {
		return simulateAfterTick(
			protocolVersion, blockCache, POSITION,
			pose, gliding, abilityFlying, allowFlying,
			0.0D, 0.0D, false
		);
	}

	private static Motion simulateAfterTick(
		int protocolVersion,
		BlockCache blockCache,
		Position position,
		Pose pose,
		boolean gliding,
		boolean abilityFlying,
		boolean allowFlying
	) {
		return simulateAfterTick(
			protocolVersion, blockCache, position,
			pose, gliding, abilityFlying, allowFlying,
			0.0D, 0.0D, false
		);
	}

	private static Motion simulateAfterTick(
		int protocolVersion,
		BlockCache blockCache,
		Position position,
		Pose pose,
		boolean gliding,
		boolean abilityFlying,
		boolean allowFlying,
		double movementEfficiency,
		double waterMovementEfficiency,
		boolean inWater
	) {
		World world = FakeWorldFactory.createWorld(
			(methodName, _) -> switch (methodName) {
				case "isChunkLoaded", "isChunkInUse" -> true;
				case "isThundering", "hasStorm" -> false;
				default -> null;
			}
		);
		Location location = position.toLocation(world);
		UUID playerId = UUID.randomUUID();
		Player player = FakePlayerFactory.createPlayer(
			(methodName, _) -> switch (methodName) {
				case "getWorld" -> world;
				case "getLocation" -> location;
				case "getUniqueId" -> playerId;
				default -> null;
			}
		);
		User user = UserFactory.createTestUserFor(player, (usr, key) -> switch (key) {
			case "blockCache" -> blockCache;
			case "protocolVersion" -> protocolVersion;
			default -> null;
		});
		UserRepository.manuallyRegisterUser(player, user);
		user.meta().abilities().setFlying(abilityFlying);
		user.meta().abilities().setAllowFlying(allowFlying);
		user.meta().abilities().modifyBaseValue("generic.movement_efficiency", movementEfficiency);
		user.meta().abilities().modifyBaseValue("generic.water_movement_efficiency", waterMovementEfficiency);

		MovementMetadata environment = user.meta().movement();
		environment.updateMovement(position, ROTATION);
		environment.setVerifiedLastPosition(position, "block speed factor test seed");
		environment.setLastPosition(position);
		environment.setPose(pose);
		environment.gliding = gliding;
		environment.setInWater(inWater);
		environment.setInLava(false);
		environment.onGround = false;
		environment.setLastOnGround(false);
		environment.setSimulationResult(SimulationResult.untouched(INPUT_MOTION.copy()));

		Simulator simulator = Simulators.selectFor(environment);
		return simulator.simulateAfterTick(
			user,
			environment.mutableView(),
			CONFIGURATION,
			position,
			INPUT_MOTION
		);
	}

	private static void assertHorizontalScale(Motion expected, Motion actual, double scale) {
		assertNotEquals(0.0D, expected.motionX, EPSILON);
		assertNotEquals(0.0D, expected.motionZ, EPSILON);
		assertEquals(expected.motionX * scale, actual.motionX, EPSILON);
		assertEquals(expected.motionY, actual.motionY, EPSILON);
		assertEquals(expected.motionZ * scale, actual.motionZ, EPSILON);
	}

	private static void assertMotionEquals(Motion expected, Motion actual) {
		assertEquals(expected.motionX, actual.motionX, EPSILON);
		assertEquals(expected.motionY, actual.motionY, EPSILON);
		assertEquals(expected.motionZ, actual.motionZ, EPSILON);
	}

	private static final class MaterialPlaneBlockCache implements BlockCache {
		private final int blockY;
		private final BlockCacheEntry blockCacheEntry;

		private MaterialPlaneBlockCache(int blockY, Material material) {
			this.blockY = blockY;
			this.blockCacheEntry = new BlockCacheEntry(
				BlockShapes.emptyShape(),
				BlockShapes.emptyShape(),
				material,
				0
			);
		}

		@Override
		public @NonNull BlockCacheEntry stateAt(int posX, int posY, int posZ) {
			return posY == blockY ? blockCacheEntry : BlockCacheEntry.empty();
		}

		@Override
		public boolean isClientSpeculatingAt(int posX, int posY, int posZ) {
			return false;
		}

		@Override
		public void setClientSpeculationValue(
			World world,
			int posX,
			int posY,
			int posZ,
			Material type,
			int variant,
			int sequenceNumber
		) {
		}

		@Override
		public void undoClientSpeculation(World world, int posX, int posY, int posZ) {
		}

		@Override
		public void moveClientSpeculationsToOverride(World world, int requiredSequenceNumber) {
		}

		@Override
		public boolean currentlyInOverride(int posX, int posY, int posZ) {
			return false;
		}

		@Override
		public void lockOverride(int posX, int posY, int posZ) {
		}

		@Override
		public void unlockOverride(int posX, int posY, int posZ) {
		}

		@Override
		public void invalidateOverride(int posX, int posY, int posZ) {
		}

		@Override
		public int numOfIndexedReplacements() {
			return 0;
		}

		@Override
		public int numOfLocatedReplacements() {
			return 0;
		}

		@Override
		public void invalidateOverridesInBounds(
			int chunkXMinPos,
			int chunkXMaxPos,
			int chunkZMinPos,
			int chunkZMaxPos
		) {
		}

		@Override
		public boolean hasOverridesInBounds(
			int chunkXMinPos,
			int chunkXMaxPos,
			int chunkZMinPos,
			int chunkZMaxPos
		) {
			return false;
		}

		@Override
		public void invalidateAll() {
		}

		@Override
		public void invalidateCache() {
		}

		@Override
		public void invalidateCacheAt(int posX, int posY, int posZ) {
		}
	}
}
