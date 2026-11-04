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
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.share.*;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static de.jpx3.intave.user.meta.ProtocolMetadata.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class BaseSimulatorPoseInputTest {
	private static final double EPSILON = 1.0E-9;
	private static final Position POSITION = Position.of(0.5, 50.0, 0.5);
	private static final Rotation ROTATION = Rotation.of(30.0F, 0.0F);
	private static final MovementConfiguration FORWARD = MovementConfiguration.blank().pressingW();

	@BeforeEach
	void setUp() {
		MinecraftVersion.setCurrent(new MinecraftVersion("26.1.2"));
	}

	@Test
	void drySwimmingPoseUsesSneakingSpeedOnTargetProtocol() {
		assertUsesCrawlingSlowdown(VER_26_1_1, Pose.SWIMMING, false);
	}

	@Test
	void drySwimmingPoseUsesCrawlingSlowdownAtModernColliderBoundary() {
		assertUsesCrawlingSlowdown(VER_1_14, Pose.SWIMMING, false);
	}

	@Test
	void preModernColliderSwimmingPoseKeepsLegacyInputSpeed() {
		Motion standing = simulate(VER_1_13_2, Pose.STANDING, false, false);
		Motion swimming = simulate(VER_1_13_2, Pose.SWIMMING, false, false);

		assertMotionEquals(standing, swimming);
	}

	@Test
	void swimmingPoseInWaterDoesNotCountAsCrawling() {
		Motion standing = simulate(VER_26_1_1, Pose.STANDING, false, true);
		Motion swimming = simulate(VER_26_1_1, Pose.SWIMMING, false, true);

		assertMotionEquals(standing, swimming);
	}

	@Test
	void waterMovementEfficiencyInterpolatesWaterAccelerationFrom121() {
		Motion normal = simulate(VER_1_21, Pose.STANDING, false, true, 0.0D);
		Motion efficient = simulate(VER_1_21, Pose.STANDING, false, true, 1.0D);
		float normalAcceleration = 0.02F;
		float efficientAcceleration = normalAcceleration
			+ (0.1F - normalAcceleration) * 0.5F;
		double expectedScale = efficientAcceleration / normalAcceleration;

		assertEquals(normal.motionX * expectedScale, efficient.motionX, 1.0E-8D);
		assertEquals(normal.motionZ * expectedScale, efficient.motionZ, 1.0E-8D);
	}

	@Test
	void pre121ClientIgnoresWaterMovementEfficiencyAttribute() {
		Motion normal = simulate(VER_1_20_5, Pose.STANDING, false, true, 0.0D);
		Motion efficient = simulate(VER_1_20_5, Pose.STANDING, false, true, 1.0D);

		assertMotionEquals(normal, efficient);
	}

	@Test
	void stoppedFallFlyingPoseRetainsCrawlingSlowdown() {
		assertUsesCrawlingSlowdown(VER_26_1_1, Pose.FALL_FLYING, false);
	}

	@Test
	void activeFallFlyingPoseDoesNotCountAsCrawling() {
		Motion standing = simulate(VER_26_1_1, Pose.STANDING, false, false);
		Motion fallFlying = simulate(VER_26_1_1, Pose.FALL_FLYING, true, false);

		assertMotionEquals(standing, fallFlying);
	}

	@Test
	void staleCrouchingPoseDoesNotSlowInputAfterSneakWasAlreadyReleased() {
		Motion standing = simulate(VER_26_1_1, Pose.STANDING, false, false);
		Motion staleCrouching = simulate(VER_26_1_1, Pose.CROUCHING, false, false);

		assertMotionEquals(standing, staleCrouching);
	}

	@Test
	void legacyStaleCrouchingPoseDoesNotSlowReleasedSneakInput() {
		Motion standing = simulate(VER_1_12, Pose.STANDING, false, false);
		Motion staleCrouching = simulate(VER_1_12, Pose.CROUCHING, false, false);

		assertMotionEquals(standing, staleCrouching);
	}

	@Test
	void legacyCurrentSneakInputSlowsStandingPose() {
		Motion standing = simulate(VER_1_12, Pose.STANDING, false, false);
		Motion crouching = simulate(
			VER_1_12, Pose.STANDING, false, false,
			true, false, new MockFullBlockStaticPlane()
		);

		assertEquals(standing.motionX * 0.3D, crouching.motionX, EPSILON);
		assertEquals(standing.motionZ * 0.3D, crouching.motionZ, EPSILON);
	}

	@Test
	void standingPoseDoesNotSlowInputOnTheFirstSneakTick() {
		Motion standing = simulate(VER_26_1_1, Pose.STANDING, false, false);
		Motion enteringCrouch = simulate(
			VER_26_1_1, Pose.STANDING, false, false,
			true, false, new MockFullBlockStaticPlane()
		);

		assertMotionEquals(standing, enteringCrouch);
	}

	@Test
	void previousSneakInputSlowsTheCurrentTick() {
		Motion standing = simulate(VER_26_1_1, Pose.STANDING, false, false);
		Motion crouching = simulate(
			VER_26_1_1, Pose.CROUCHING, false, false,
			false, true, new MockFullBlockStaticPlane()
		);

		assertEquals(standing.motionX * 0.3D, crouching.motionX, EPSILON);
		assertEquals(standing.motionZ * 0.3D, crouching.motionZ, EPSILON);
	}

	@Test
	void forcedCrouchingStillSlowsInputWhenStandingPoseIsObstructed() {
		Motion standing = simulate(VER_26_1_1, Pose.STANDING, false, false);
		Motion forcedCrouching = simulate(
			VER_26_1_1, Pose.CROUCHING, false, false,
			false, false, new UpperSlabBlockCache()
		);

		assertEquals(standing.motionX * 0.3D, forcedCrouching.motionX, EPSILON);
		assertEquals(standing.motionZ * 0.3D, forcedCrouching.motionZ, EPSILON);
	}

	@Test
	void activeFlightUsesStandingPoseWhenSneakingOnModernClients() {
		MovementMetadata environment = flyingSneakEnvironment(
			VER_1_14, new MockFullBlockStaticPlane()
		);

		environment.updatePose();

		assertEquals(Pose.STANDING, environment.pose());
		assertEquals(1.8F, environment.height());
	}

	@Test
	void activeFlightSuppressesDelayedSneakPoseOnModernClients() {
		MovementMetadata environment = flyingSneakEnvironment(
			VER_1_21_2, new MockFullBlockStaticPlane()
		);

		environment.updatePose();

		assertEquals(Pose.STANDING, environment.pose());
		assertEquals(1.8F, environment.height());
	}

	@Test
	void activeFlightStillUsesCrouchingFallbackWhenStandingIsObstructed() {
		MovementMetadata environment = flyingSneakEnvironment(
			VER_1_14, new UpperSlabBlockCache()
		);

		environment.updatePose();

		assertEquals(Pose.CROUCHING, environment.pose());
		assertEquals(1.5F, environment.height());
	}

	@Test
	void prePoseClientsRetainSneakingDimensionsDuringFlight() {
		MovementMetadata environment = flyingSneakEnvironment(
			VER_1_13_2, new MockFullBlockStaticPlane()
		);

		environment.updatePose();

		assertEquals(Pose.CROUCHING, environment.pose());
		assertEquals(1.5F, environment.height());
	}

	@Test
	void vehicleCollisionBoxUsesTheMountedEntityDimensions() {
		User user = UserFactory.createFallback();
		MovementMetadata environment = user.meta().movement();
		for (HitboxSize size : new HitboxSize[]{HitboxSize.of(1.3964844F, 1.6F), HitboxSize.of(0.9F, 0.9F)}) {
			environment.restoreRecordedVehicle(new Entity(5, new EntityTypeData("horse", size, 5, true, 11), false));
			environment.setSimulator(Simulators.HORSE);
			environment.updateSize();
			BoundingBox box = BoundingBox.fromPosition(user, environment, POSITION);
			assertEquals(size.width(), box.maxX - box.minX, EPSILON);
			assertEquals(size.height(), box.maxY - box.minY, EPSILON);
			assertFalse(box.intersectsWith(BoundingBox.fromBounds(-2, 51.7, -2, 2, 52, 2)));
		}
		environment.setSimulator(Simulators.BOAT);
		environment.updateSize();
		assertEquals(1.375F, environment.width());
		assertEquals(0.5625F, environment.height());
		environment.restoreRecordedVehicle(null);
		environment.setSimulator(Simulators.PLAYER);
		environment.setPose(Pose.STANDING);
		assertEquals(0.6F, environment.width());
		assertEquals(1.8F, environment.height());
	}

	private static void assertUsesCrawlingSlowdown(
		int protocolVersion,
		Pose pose,
		boolean gliding
	) {
		Motion normal = simulate(protocolVersion, Pose.STANDING, false, false);
		Motion crawling = simulate(protocolVersion, pose, gliding, false);

		assertNotEquals(0.0D, normal.motionX, EPSILON);
		assertNotEquals(0.0D, normal.motionZ, EPSILON);
		assertEquals(normal.motionX * 0.3D, crawling.motionX, EPSILON);
		assertEquals(normal.motionZ * 0.3D, crawling.motionZ, EPSILON);
	}

	private static Motion simulate(
		int protocolVersion,
		Pose pose,
		boolean gliding,
		boolean inWater
	) {
		return simulate(
			protocolVersion, pose, gliding, inWater,
			false, false, new MockFullBlockStaticPlane()
		);
	}

	private static MovementMetadata flyingSneakEnvironment(
		int protocolVersion,
		BlockCache blockCache
	) {
		World world = FakeWorldFactory.createWorld(
			(methodName, _) -> switch (methodName) {
				case "isChunkLoaded", "isChunkInUse" -> true;
				case "isThundering", "hasStorm" -> false;
				default -> null;
			}
		);
		Location location = POSITION.toLocation(world);
		Player player = FakePlayerFactory.createPlayer(
			(methodName, _) -> switch (methodName) {
				case "getWorld" -> world;
				case "getLocation" -> location;
				case "getUniqueId" -> new UUID(1L, protocolVersion);
				default -> null;
			}
		);
		User user = UserFactory.createTestUserFor(player, (usr, key) -> switch (key) {
			case "blockCache" -> blockCache;
			case "protocolVersion" -> protocolVersion;
			default -> null;
		});
		UserRepository.manuallyRegisterUser(player, user);
		user.meta().abilities().setAllowFlying(true);
		user.meta().abilities().setFlying(true);

		MovementMetadata environment = user.meta().movement();
		environment.updateMovement(POSITION, ROTATION);
		environment.setVerifiedLastPosition(POSITION, "flight pose test seed");
		environment.setLastPosition(POSITION);
		environment.setSneaking(true);
		environment.lastSneaking = true;
		environment.setPose(Pose.CROUCHING);
		return environment;
	}

	private static Motion simulate(
		int protocolVersion,
		Pose pose,
		boolean gliding,
		boolean inWater,
		double waterMovementEfficiency
	) {
		return simulate(
			protocolVersion, pose, gliding, inWater,
			false, false, new MockFullBlockStaticPlane(),
			waterMovementEfficiency
		);
	}

	private static Motion simulate(
		int protocolVersion,
		Pose pose,
		boolean gliding,
		boolean inWater,
		boolean sneaking,
		boolean lastSneaking,
		BlockCache blockCache
	) {
		return simulate(
			protocolVersion, pose, gliding, inWater,
			sneaking, lastSneaking, blockCache, 0.0D
		);
	}

	private static Motion simulate(
		int protocolVersion,
		Pose pose,
		boolean gliding,
		boolean inWater,
		boolean sneaking,
		boolean lastSneaking,
		BlockCache blockCache,
		double waterMovementEfficiency
	) {
		World world = FakeWorldFactory.createWorld(
			(methodName, _) -> switch (methodName) {
				case "isChunkLoaded", "isChunkInUse" -> true;
				case "isThundering", "hasStorm" -> false;
				default -> null;
			}
		);
		Location location = POSITION.toLocation(world);
		Player player = FakePlayerFactory.createPlayer(
			(methodName, _) -> switch (methodName) {
				case "getWorld" -> world;
				case "getLocation" -> location;
				case "getUniqueId" -> new UUID(0L, protocolVersion);
				default -> null;
			}
		);
		User user = UserFactory.createTestUserFor(player, (usr, key) -> switch (key) {
			case "blockCache" -> blockCache;
			case "protocolVersion" -> protocolVersion;
			default -> null;
		});
		UserRepository.manuallyRegisterUser(player, user);
		user.meta().abilities().modifyBaseValue(
			"generic.water_movement_efficiency", waterMovementEfficiency
		);

		MovementMetadata environment = user.meta().movement();
		environment.updateMovement(POSITION, ROTATION);
		environment.setVerifiedLastPosition(POSITION, "pose input test seed");
		environment.setLastPosition(POSITION);
		environment.setLastSprinting(false);
		environment.setSneaking(sneaking);
		environment.lastInput = new Input(
			false, false, false, false, false, lastSneaking, false
		);
		environment.lastSneaking = lastSneaking;
		environment.setInWater(inWater);
		environment.gliding = gliding;
		environment.setPose(pose);
		environment.setLastOnGround(false);

		return Simulators.PLAYER.simulateTick(
			user,
			Motion.newEmpty(),
			environment.mutableView(),
			FORWARD
		).offsetMotion().copy();
	}

	private static void assertMotionEquals(Motion expected, Motion actual) {
		assertEquals(expected.motionX, actual.motionX, EPSILON);
		assertEquals(expected.motionY, actual.motionY, EPSILON);
		assertEquals(expected.motionZ, actual.motionZ, EPSILON);
	}

	private static final class UpperSlabBlockCache implements BlockCache {
		private static final BlockShape SHAPE = BoundingBox.fromBounds(
			0.0D, 51.6D, 0.0D,
			1.0D, 52.0D, 1.0D
		);
		private static final BlockState STATE = new BlockState(
			SHAPE, SHAPE, org.bukkit.Material.STONE, 0
		);

		@Override
		public @NotNull BlockState stateAt(int posX, int posY, int posZ) {
			return posX == 0 && posY == 51 && posZ == 0 ? STATE : BlockState.empty();
		}

		@Override
		public boolean isClientSpeculatingAt(int posX, int posY, int posZ) {
			return false;
		}

		@Override
		public void setClientSpeculationValue(
			World world, int posX, int posY, int posZ,
			org.bukkit.Material type, int variant, int sequenceNumber
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
			int chunkXMinPos, int chunkXMaxPos,
			int chunkZMinPos, int chunkZMaxPos
		) {
		}

		@Override
		public boolean hasOverridesInBounds(
			int chunkXMinPos, int chunkXMaxPos,
			int chunkZMinPos, int chunkZMaxPos
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
