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
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.block.fluid.FluidFlow;
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.check.movement.physics.update.CausalConstraint;
import de.jpx3.intave.check.movement.physics.update.Reduce;
import de.jpx3.intave.share.BlockCacheEntry;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static de.jpx3.intave.user.meta.ProtocolMetadata.*;
import static org.junit.jupiter.api.Assertions.*;

final class BaseSimulatorAquaticsTest {
	private static final double SNEAK_DESCENT = (double) -0.04F;
	private static final double EPSILON = 1.0E-12;

	@BeforeEach
	void setUp() {
		MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
	}

	@Test
	void preTickRefreshesSwimmingAfterWaterStateWithoutChangingSiblingBranches() {
		TestContext context = context(VER_1_21_4, false, Motion.newEmpty(), new MockFullBlockStaticPlane());
		context.environment.setInWater(true);
		context.environment.setSwimming(true);
		context.environment.setLastSprinting(true);
		SimulationEnvironment branch = context.environment.mutableView();

		Simulators.PLAYER.simulatePreTick(context.user, Motion.newEmpty(), branch);

		assertFalse(branch.inWater());
		assertFalse(branch.isSwimming());
		assertTrue(context.environment.inWater());
		assertTrue(context.environment.isSwimming());
	}

	@Test
	void attackReductionIsClampedInPreTickAndLeavesVerticalMotionUnscaled() {
		TestContext context = context(VER_1_21_4, false, Motion.newEmpty(), new MockFullBlockStaticPlane());
		context.environment.setBaseMotion(0.004, 0.2, -0.004);
		SimulationEnvironment branch = context.environment.mutableView();
		new Reduce(false,
			CausalConstraint.openEnded(0, 1)).applyTo(branch);

		Motion motion = Simulators.PLAYER.simulatePreTick(context.user, branch.mutableBaseMotionCopy(), branch);

		assertEquals(0.0, motion.motionX);
		assertEquals(0.2, motion.motionY);
		assertEquals(0.0, motion.motionZ);
		assertEquals(new Motion(0.004, 0.2, -0.004), context.environment.mutableBaseMotionCopy());
	}

	@Test
	void leavingWaterWhileSneakingUsesRefreshedWaterState() {
		TestContext context = context(VER_1_17, false, Motion.newEmpty(), new MockFullBlockStaticPlane());
		context.environment.setInWater(true);
		context.environment.setSneaking(true);

		Motion result = Simulators.PLAYER.simulatePreTick(
			context.user, Motion.newEmpty(), context.environment
		);

		assertFalse(context.environment.inWater());
		assertEquals(0.0, result.motionY, EPSILON);
	}

	@Test
	void enteringWaterWhileSneakingAppliesDescentAfterFluidFlow() {
		TestContext context = context(VER_1_13, true, new Motion(0.0, 0.01, 0.0), new MockFullBlockStaticPlane());
		context.environment.setInWater(false);
		context.environment.setSneaking(true);

		Motion result = Simulators.PLAYER.simulatePreTick(
			context.user, Motion.newEmpty(), context.environment
		);

		assertTrue(context.environment.inWater());
		assertEquals(0.01 + SNEAK_DESCENT, result.motionY, EPSILON);
	}

	@Test
	void pushOutOfBlocksRunsAfterFluidFlow() {
		TestContext context = context(
			VER_1_17,
			true,
			new Motion(0.014, 0.0, 0.0),
			new SingleSolidBlockCache(0, 50, 0)
		);

		Motion result = Simulators.PLAYER.simulatePreTick(
			context.user, Motion.newEmpty(), context.environment
		);

		assertEquals(0.1, result.motionX, EPSILON);
	}

	@Test
	void modernFlyingPlayerIsNotMovedDownByFluidSneak() {
		TestContext context = context(VER_1_17, true, Motion.newEmpty(), new MockFullBlockStaticPlane());
		context.user.meta().abilities().setFlying(true);
		context.environment.setSneaking(true);

		Motion result = Simulators.PLAYER.simulatePreTick(
			context.user, Motion.newEmpty(), context.environment
		);

		assertEquals(0.0, result.motionY, EPSILON);
	}

	@Test
	void pre117FlyingPlayerRetainsLegacyFluidSneakBehavior() {
		TestContext context = context(VER_1_16, true, Motion.newEmpty(), new MockFullBlockStaticPlane());
		context.user.meta().abilities().setFlying(true);
		context.environment.setSneaking(true);

		Motion result = Simulators.PLAYER.simulatePreTick(
			context.user, Motion.newEmpty(), context.environment
		);

		assertEquals(SNEAK_DESCENT, result.motionY, EPSILON);
	}

	@Test
	void late116FlyingPlayerIsNotMovedDownByFluidSneak() {
		TestContext context = context(VER_1_16_4, true, Motion.newEmpty(), new MockFullBlockStaticPlane());
		context.user.meta().abilities().setFlying(true);
		context.environment.setSneaking(true);

		Motion result = Simulators.PLAYER.simulatePreTick(
			context.user, Motion.newEmpty(), context.environment
		);

		assertEquals(0.0, result.motionY, EPSILON);
	}

	@Test
	void flyingPlayerDetectsWaterWithoutReceivingFlowAcrossVersions() {
		for (int protocolVersion : new int[] {VER_1_8, VER_1_15, VER_1_15_2, VER_26_3}) {
			TestContext context = context(
				protocolVersion, true, new Motion(0.02, 0.0, 0.0),
				new MockFullBlockStaticPlane()
			);
			context.user.meta().abilities().setFlying(true);

			Motion result = Simulators.PLAYER.simulatePreTick(
				context.user, Motion.newEmpty(), context.environment
			);

			assertEquals(0.0, result.motionX, EPSILON);
			assertTrue(context.environment.inWater());
		}
	}

	@Test
	void preAquaticClientDoesNotApplyFluidSneakDescent() {
		TestContext context = context(VER_1_12, true, Motion.newEmpty(), new MockFullBlockStaticPlane());
		context.environment.setSneaking(true);

		Motion result = Simulators.PLAYER.simulatePreTick(
			context.user, Motion.newEmpty(), context.environment
		);

		assertEquals(0.0, result.motionY, EPSILON);
	}

	@Test
	void eyeFluidStateStagingStartsWith116() {
		TestContext legacy = context(VER_1_15, false, Motion.newEmpty(), new MockFullBlockStaticPlane());
		TestContext modern = context(VER_1_16, false, Motion.newEmpty(), new MockFullBlockStaticPlane());

		assertFalse(legacy.user.meta().protocol().stagesEyeFluidState());
		assertTrue(modern.user.meta().protocol().stagesEyeFluidState());
	}

	@Test
	void aquaticFluidFlowOwnsTheSingleInteractionMargin() {
		TestContext context = context(VER_26_1_1, false, Motion.newEmpty(), new MockFullBlockStaticPlane());
		BoundingBox entityBox = context.environment.boundingBox();

		Simulators.PLAYER.simulatePreTick(
			context.user, Motion.newEmpty(), context.environment
		);

		FixedWaterFlow waterFlow = (FixedWaterFlow) context.user.fluidflow();
		assertSame(entityBox, waterFlow.lastBoundingBox);
	}

	private static TestContext context(
		int protocolVersion,
		boolean currentWaterState,
		Motion waterFlow,
		BlockCache blockCache
	) {
		World world = FakeWorldFactory.createWorld(
			(methodName, _) -> switch (methodName) {
				case "isChunkLoaded", "isChunkInUse" -> true;
				case "isThundering", "hasStorm" -> false;
				default -> null;
			}
		);
		Location location = new Location(world, 0.5, 50.0, 0.5);
		UUID playerId = new UUID(0L, protocolVersion);
		Player player = FakePlayerFactory.createPlayer(
			(methodName, _) -> switch (methodName) {
				case "getWorld" -> world;
				case "getLocation" -> location;
				case "getUniqueId" -> playerId;
				default -> null;
			}
		);
		FluidFlow fluidFlow = new FixedWaterFlow(currentWaterState, waterFlow);
		User user = UserFactory.createTestUserFor(player, (usr, key) -> switch (key) {
			case "blockCache" -> blockCache;
			case "protocolVersion" -> protocolVersion;
			case "waterflow" -> fluidFlow;
			default -> null;
		});
		UserRepository.manuallyRegisterUser(player, user);

		MockSimulationEnvironment environment = new MockSimulationEnvironment(user);
		environment.setLastPosition(0.5, 50.0, 0.5);
		environment.setBoundingBox(BoundingBox.fromBounds(0.2, 50.0, 0.2, 0.8, 51.8, 0.8));
		environment.setResetMotion(0.003F);
		return new TestContext(user, environment);
	}

	private record TestContext(User user, MockSimulationEnvironment environment) {
	}

	private static final class FixedWaterFlow implements FluidFlow {
		private final boolean inWater;
		private final Motion waterFlow;
		private BoundingBox lastBoundingBox;

		private FixedWaterFlow(boolean inWater, Motion waterFlow) {
			this.inWater = inWater;
			this.waterFlow = waterFlow;
		}

		@Override
		public boolean applyWaterFlowTo(
			User user,
			SimulationEnvironment environment,
			Motion baseMotion,
			BoundingBox boundingBox
		) {
			lastBoundingBox = boundingBox;
			baseMotion.add(waterFlow);
			return inWater;
		}

		@Override
		public boolean applyLavaFlowTo(
			User user,
			SimulationEnvironment environment,
			Motion baseMotion,
			BoundingBox boundingBox
		) {
			return false;
		}

		@Override
		public double fluidDepthAt(User user, BoundingBox boundingBox) {
			return 0.0;
		}

		@Override
		public Motion pushMotionAt(User user, int blockX, int blockY, int blockZ) {
			return Motion.newEmpty();
		}
	}

	private static final class SingleSolidBlockCache implements BlockCache {
		private final int blockX;
		private final int blockY;
		private final int blockZ;

		private SingleSolidBlockCache(int blockX, int blockY, int blockZ) {
			this.blockX = blockX;
			this.blockY = blockY;
			this.blockZ = blockZ;
		}

		@Override
		public @NonNull BlockCacheEntry stateAt(int posX, int posY, int posZ) {
			return posX == blockX && posY == blockY && posZ == blockZ
				? BlockCacheEntry.stone()
				: BlockCacheEntry.empty();
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
