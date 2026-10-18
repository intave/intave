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

package de.jpx3.intave.check.movement.physics.recording;

import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.block.cache.PlaybackBlockCacheView;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.physics.BlockPhysics;
import de.jpx3.intave.block.shape.resolve.DenyShapeResolverPipeline;
import de.jpx3.intave.block.shape.resolve.DrillResolver;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.check.movement.physics.environment.PostTickSimulation;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.search.SimulationSearch;
import de.jpx3.intave.check.movement.physics.search.ThreeTickSimulationSearch;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.check.movement.physics.update.MotionUpdate;
import de.jpx3.intave.check.movement.physics.update.PistonActionUpdate;
import de.jpx3.intave.check.movement.physics.update.Reduce;
import de.jpx3.intave.check.movement.physics.update.ShulkerBoxActionUpdate;
import de.jpx3.intave.module.test.record.MoveFrame;
import de.jpx3.intave.module.test.record.MovementFrameState;
import de.jpx3.intave.module.test.record.MovementRecording;
import de.jpx3.intave.module.test.record.TickRange;
import de.jpx3.intave.module.test.record.action.ActiveItemReplacementAction;
import de.jpx3.intave.module.test.record.action.Action;
import de.jpx3.intave.module.test.record.action.AttackReduction;
import de.jpx3.intave.module.test.record.action.ItemUsePacketAction;
import de.jpx3.intave.module.test.record.action.PistonSlimeAction;
import de.jpx3.intave.module.test.record.action.ReceiveVelocity;
import de.jpx3.intave.module.test.record.action.ShulkerBoxAction;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.share.*;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.test.MockEmptyInventory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.*;
import static de.jpx3.intave.check.movement.physics.search.PostTickMotionType.SENT_OFFSET_MOTION;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_14;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_21_2;
import static org.junit.jupiter.api.Assertions.*;

final class MovementRecordingPhysicsTests {
	private static final double DIVERGED_MOTION_DISTANCE = 0.01;

	@FunctionalInterface
	interface MovementObserver {
		void observe(int tick, Simulation simulation, Motion actualMotion);
	}

	@BeforeAll
	static void setup() {
		System.out.println("--------------------------------");
		System.out.println("Movement recording physics tests");
		System.out.println("--------------------------------");
		System.out.println();
	}

	@Test
	void simulationProcessorProcessesAllRecordedMovements() throws IOException {
		List<Path> recordingPaths = findMovementRecordings();
		assertFalse(recordingPaths.isEmpty(), "No movement recordings were found");

		for (Path recordingPath : recordingPaths) {
			String fileName = recordingPath.getFileName().toString();
			if (fileName.startsWith("_")) {
				System.out.println("[SKIPPED] " + recordingPath);
				continue;
			}
			processRecordingResource(resourcePathOf(recordingPath));
		}
	}

	@Test
	void elytraFireworksWaterRecording() throws IOException {
		processRecordingResource(
			"physics_test_runs/pose/elytra/elytra_fireworks_water.ptr"
		);
	}

	@Test
	void flying2Recording() throws IOException {
		processRecordingResource(
			"physics_test_runs/attributes/flying_2.ptr"
		);
	}

	@Test
	void flying3Recording() throws IOException {
		processRecordingResource(
			"physics_test_runs/attributes/flying_3.ptr"
		);
	}

	@Test
	void flying4Recording() throws IOException {
		processRecordingResource(
			"physics_test_runs/attributes/flying_4.ptr"
		);
	}

	@Test
	void woodenSwordBlockingRecording() throws IOException {
		processRecordingResource(
			"physics_test_runs/use/sword_1.8on1.8.ptr"
		);
		processRecordingResource(
			"physics_test_runs/use/sword_1.8on1.8_2.ptr"
		);
	}

	@Test
	void legacyEatThenSwordNoSlowRecordingIsRejected() {
		AssertionError failure = assertThrows(
			AssertionError.class,
			() -> processRecordingResource(
				"physics_test_runs/use/_no_slow_eat_then_sword_1.8on1.8.ptr"
			)
		);
		assertTrue(
			failure.getMessage().contains("tick 25"),
			"Expected the first no-slow divergence at tick 25, got: "
				+ failure.getMessage()
		);
	}

	@Test
	void recordedGlidingStateSelectsElytra() {
		MovementRecording recording = MovementRecording.create(
			VER_1_21_2,
			MinecraftVersions.VER1_21_4
		);
		Position position = new Position(0, 50, 0);
		Rotation rotation = Rotation.zero();
		recording.insertFrame(
			BoundingBox.empty(),
			Input.none(),
			position,
			rotation,
			new MockFullBlockStaticPlane(),
			true
		);
		MoveFrame frame = recording.frames().get(0);
		preparePhysicsTestRuntime(recording);

		PlaybackBlockCacheView blockCache = new PlaybackBlockCacheView(recording);
		blockCache.updateBlocks(frame.blocks());
		World world = createReplayWorld();
		AtomicReference<Location> currentLocation = new AtomicReference<>(
			locationOf(world, position, rotation)
		);
		User user = createReplayUser(recording, blockCache, world, currentLocation);
		MovementMetadata metadata = user.meta().movement();

		metadata.gliding = frame.gliding();
		seedInitialMovementState(user, metadata, position, rotation);

		assertEquals(Pose.STANDING, metadata.pose());
		assertSame(Simulators.ELYTRA, Simulators.selectFor(metadata));

		metadata.tickComplete(true, true, true);

		assertEquals(Pose.FALL_FLYING, metadata.pose());

		metadata.gliding = false;
		metadata.updateMovement(position, rotation);

		assertEquals(Pose.FALL_FLYING, metadata.pose());
		assertSame(Simulators.PLAYER, Simulators.selectFor(metadata));

		metadata.tickComplete(true, true, true);

		assertEquals(Pose.STANDING, metadata.pose());
	}

	@Test
	void recordedFrameStateRestoresPreviouslyUnrepresentableSimulatorInputs() {
		MovementRecording recording = MovementRecording.create(
			VER_1_21_2,
			MinecraftVersions.VER1_21_4
		);
		preparePhysicsTestRuntime(recording);

		Position position = new Position(0, 64, 0);
		Rotation rotation = Rotation.zero();
		AtomicReference<Location> currentLocation = new AtomicReference<>();
		World world = createReplayWorld();
		currentLocation.set(locationOf(world, position, rotation));
		PlaybackBlockCacheView blockCache = new PlaybackBlockCacheView(recording);

		MovementFrameState.ItemState elytra = new MovementFrameState.ItemState(
			org.bukkit.Material.ELYTRA, 1, 17, Map.of()
		);
		MovementFrameState state = new MovementFrameState(
			new MovementFrameState.AbilityState(true, true, false, false, 0.08F, "CREATIVE"),
			new MovementFrameState.EffectState(2, 40, 1, 20, 3, 10, Collections.emptyList()),
			new MovementFrameState.InventoryState(
				List.of(new MovementFrameState.ItemState(org.bukkit.Material.BOW, 1, 0, Map.of())),
				Arrays.asList(null, null, elytra, null),
				null, 0, true, 5, 0, 2, false, org.bukkit.Material.BOW,
				false, false, org.bukkit.Material.AIR, true, false,
				PlayerHand.MAIN_HAND, false, 0.2F
			),
			3, 2,
			new MovementFrameState.EntityState(
				42, true, "Boat", 1, false, 10,
				1.375F, 0.5625F, false, true, 0.0, 64.0, 0.0
			),
			List.of(new MovementFrameState.EntityState(
				43, true, "Zombie", 54, true, 9,
				0.6F, 1.95F, false, true, 0.1, 64.0, 0.1
			)),
			"IN_WATER", "IN_AIR", 0.8F, 64.7D,
			2, 0, 4
		);
		ReplayPlayerState replayState = new ReplayPlayerState(state);
		User user = createReplayUser(recording, blockCache, world, currentLocation, replayState);

		replayState.apply(user, state);

		assertSame(Simulators.BOAT, Simulators.selectFor(user.meta().movement()));
		assertTrue(user.meta().abilities().flying());
		assertTrue(user.meta().movement().hasElytraEquipped());
		assertEquals(2, user.meta().movement().activeFireworkRockets());
		assertEquals(1, user.meta().connection().tracedEntities().size());
		assertEquals(2, user.meta().potions().potionEffectSpeedAmplifier());
		assertTrue(user.meta().inventory().handActive());
		assertEquals(PlayerHand.MAIN_HAND, user.meta().inventory().activeHand());
		assertFalse(user.meta().inventory().activeItemAllowsSprinting());
		assertEquals(0.2F, user.meta().inventory().activeItemUseSpeedMultiplier());
		assertEquals(org.bukkit.Material.BOW, user.meta().inventory().heldItemType());
		assertEquals(2, user.meta().movement().reduceTicks());
		assertEquals(0, user.meta().movement().ticksPast(ATTACK_REDUCE));
		assertEquals(4, user.meta().movement().ticksPast(ENTITY_USE));
	}

	@Test
	void recordedReductionsKeepTheirTickBoundaryMarkers() {
		MovementRecording recording = MovementRecording.create(
			VER_1_21_2,
			MinecraftVersions.VER1_21_4
		);
		preparePhysicsTestRuntime(recording);

		Position position = new Position(0, 64, 0);
		Rotation rotation = Rotation.zero();
		World world = createReplayWorld();
		AtomicReference<Location> currentLocation = new AtomicReference<>(
			locationOf(world, position, rotation)
		);
		User user = createReplayUser(
			recording,
			new PlaybackBlockCacheView(recording),
			world,
			currentLocation
		);
		MovementMetadata metadata = user.meta().movement();
		TickRange nextFrame = TickRange.betweenExclusive(0, 1);

		applyActionsForTick(List.of(
			new AttackReduction(true, true, nextFrame),
			new AttackReduction(true, false, nextFrame)
		), user, 0);

		assertEquals(2, metadata.allTickAmbiguousUpdates().size());
		Reduce first = assertInstanceOf(Reduce.class, metadata.allTickAmbiguousUpdates().get(0));
		Reduce second = assertInstanceOf(Reduce.class, metadata.allTickAmbiguousUpdates().get(1));
		assertTrue(first.lastActiveInTick());
		assertTrue(second.lastActiveInTick());
		assertTrue(first.mandatory());
		assertFalse(second.mandatory());
	}

	@Test
	void recordedMinecraft18SlotPacketStopsUseWithoutSyntheticReset() {
		MovementRecording recording = MovementRecording.create(
			47,
			MinecraftVersions.VER1_21_4
		);
		preparePhysicsTestRuntime(recording);

		Position position = new Position(0, 64, 0);
		Rotation rotation = Rotation.zero();
		World world = createReplayWorld();
		AtomicReference<Location> currentLocation = new AtomicReference<>(
			locationOf(world, position, rotation)
		);
		User user = createReplayUser(
			recording,
			new PlaybackBlockCacheView(recording),
			world,
			currentLocation
		);
		user.player().getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
		user.player().getInventory().setItem(1, new ItemStack(Material.IRON_SWORD));
		user.meta().inventory().setHeldItemSlot(0);

		List<Action> actions = List.of(
			ItemUsePacketAction.useItem(PlayerHand.MAIN_HAND, 0),
			ItemUsePacketAction.heldItemSlot(1, 2)
		);
		applyActionsForTick(actions, user, 0);
		applyPacketDrivenItemUseBeforePhysics(user, true);

		assertTrue(user.meta().inventory().handActive());

		applyActionsForTick(actions, user, 1);
		applyPacketDrivenItemUseBeforePhysics(user, true);

		assertEquals(0, user.meta().inventory().handSlot());
		assertTrue(user.meta().inventory().handActive());

		applyActionsForTick(actions, user, 2);
		applyPacketDrivenItemUseBeforePhysics(user, false);

		assertEquals(1, user.meta().inventory().handSlot());
		assertFalse(user.meta().inventory().releaseItemNextTick);
		assertFalse(user.meta().inventory().handActive());
	}

	@Test
	void recordedMinecraft18ActiveStackReplacementEndsSwordUse() {
		MovementRecording recording = MovementRecording.create(
			47,
			MinecraftVersions.VER1_21_4
		);
		preparePhysicsTestRuntime(recording);
		World world = createReplayWorld();
		User user = createReplayUser(
			recording,
			new PlaybackBlockCacheView(recording),
			world,
			new AtomicReference<>(new Location(world, 0.0D, 64.0D, 0.0D))
		);
		user.player().getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
		user.meta().inventory().setHeldItemSlot(0);
		List<Action> actions = List.of(
			ItemUsePacketAction.useItem(PlayerHand.MAIN_HAND, 0),
			ActiveItemReplacementAction.at(Material.DIAMOND_SWORD, 1)
		);

		applyActionsForTick(actions, user, 0);
		applyPacketDrivenItemUseBeforePhysics(user, true);
		assertTrue(user.meta().inventory().handActive());

		applyActionsForTick(actions, user, 1);
		applyPacketDrivenItemUseBeforePhysics(user, true);
		assertFalse(user.meta().inventory().handActive());
	}

	@Test
	void recordedMinecraft19MainHandUseEndsAtTheSlotPacket() {
		MovementRecording recording = MovementRecording.create(
			ProtocolMetadata.VER_1_9,
			MinecraftVersions.VER1_21_4
		);
		preparePhysicsTestRuntime(recording);
		World world = createReplayWorld();
		User user = createReplayUser(
			recording,
			new PlaybackBlockCacheView(recording),
			world,
			new AtomicReference<>(new Location(world, 0.0D, 64.0D, 0.0D))
		);
		user.player().getInventory().setItem(0, new ItemStack(Material.BOW));
		user.player().getInventory().setItem(1, new ItemStack(Material.SHIELD));
		user.player().getInventory().setItem(2, new ItemStack(Material.ARROW));
		user.meta().inventory().setHeldItemSlot(0);
		List<Action> actions = List.of(
			ItemUsePacketAction.useItem(PlayerHand.MAIN_HAND, 0),
			ItemUsePacketAction.heldItemSlot(1, 2)
		);

		applyActionsForTick(actions, user, 0);
		applyPacketDrivenItemUseBeforePhysics(user, true);
		assertTrue(user.meta().inventory().handActive());

		applyActionsForTick(actions, user, 2);
		applyPacketDrivenItemUseBeforePhysics(user, true);
		assertFalse(user.meta().inventory().handActive());
		assertEquals(1, user.meta().inventory().handSlot());
	}

	@Test
	void recordedMinecraft19OffhandUseSurvivesMainHandSlotPacket() {
		MovementRecording recording = MovementRecording.create(
			ProtocolMetadata.VER_1_9,
			MinecraftVersions.VER1_21_4
		);
		preparePhysicsTestRuntime(recording);
		World world = createReplayWorld();
		User user = createReplayUser(
			recording,
			new PlaybackBlockCacheView(recording),
			world,
			new AtomicReference<>(new Location(world, 0.0D, 64.0D, 0.0D))
		);
		user.player().getInventory().setItem(0, new ItemStack(Material.BOW));
		user.player().getInventory().setItem(1, new ItemStack(Material.BOW));
		user.player().getInventory().setItem(2, new ItemStack(Material.ARROW));
		user.player().getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
		user.meta().inventory().setHeldItemSlot(0);
		List<Action> actions = List.of(
			ItemUsePacketAction.useItem(PlayerHand.OFF_HAND, 0),
			ItemUsePacketAction.heldItemSlot(1, 2)
		);

		applyActionsForTick(actions, user, 0);
		applyPacketDrivenItemUseBeforePhysics(user, true);
		applyActionsForTick(actions, user, 2);
		applyPacketDrivenItemUseBeforePhysics(user, true);

		assertTrue(user.meta().inventory().handActive());
		assertEquals(PlayerHand.OFF_HAND, user.meta().inventory().activeHand());
		assertEquals(1, user.meta().inventory().handSlot());
	}

	@Test
	void recordedGroundStateIsAppliedAtClientTickBoundaries() {
		MovementRecording recording = MovementRecording.create(
			VER_1_21_2,
			MinecraftVersions.VER1_21_4
		);
		preparePhysicsTestRuntime(recording);

		Position position = new Position(0, 64, 0);
		Rotation rotation = Rotation.zero();
		AtomicReference<Location> currentLocation = new AtomicReference<>();
		World world = createReplayWorld();
		currentLocation.set(locationOf(world, position, rotation));
		PlaybackBlockCacheView blockCache = new PlaybackBlockCacheView(recording);
		User user = createReplayUser(recording, blockCache, world, currentLocation);
		MovementMetadata metadata = user.meta().movement();

		metadata.onGround = true;
		metadata.lastOnGround = true;
		applyRecordedGroundBeforeTick(metadata, false);

		assertFalse(metadata.onGround());
		assertFalse(metadata.lastOnGround());

		applyRecordedGroundAfterMove(metadata, true);

		assertTrue(metadata.onGround());
		assertFalse(metadata.lastOnGround());

		applyRecordedGroundBeforeTick(metadata, null);

		assertTrue(metadata.onGround());
		assertFalse(metadata.lastOnGround());
	}

	@Test
	void recordedPoseDistinguishesNormalAndRejectedElytraStops() {
		MovementRecording recording = MovementRecording.loadFrom(
			Resources.resourceFromJarOrTestBuild(
				"physics_test_runs/pose/elytra/elytra_jump.ptr"
			)
		);

		assertEquals(Pose.STANDING, recording.frames().get(47).physicalPose());
		assertEquals(Pose.FALL_FLYING, recording.frames().get(169).physicalPose());
	}

	static void processRecordingResource(String resourcePath) throws IOException {
		processRecordingResource(resourcePath, (tick, simulation, actualMotion) -> {
		});
	}

	static void processRecordingResource(
		String resourcePath,
		MovementObserver observer
	) throws IOException {
		MovementRecording recording = MovementRecording.loadFrom(
			Resources.resourceFromJarOrTestBuild(resourcePath)
		);
		preparePhysicsTestRuntime(recording);
		processRecording(resourcePath, recording, observer);
	}

	static void processRecording(
		String resourcePath,
		MovementRecording recording
	) {
		processRecording(resourcePath, recording, (tick, simulation, actualMotion) -> {
		});
	}

	private static void processRecording(
		String resourcePath,
		MovementRecording recording,
		MovementObserver observer
	) {
		System.out.print("\r[START] " + resourcePath + "...");
		List<MoveFrame> frames = recording.frames();
		int firstPositionFrame = firstPositionFrame(frames);
		if (firstPositionFrame < 0) {
			fail(resourcePath + " does not contain a position frame");
		}

		PlaybackBlockCacheView blockCache = new PlaybackBlockCacheView(recording);
		for (int tick = 0; tick <= firstPositionFrame; tick++) {
			blockCache.updateBlocks(frames.get(tick).blocks());
		}

		MoveFrame firstFrame = frames.get(firstPositionFrame);
		// Versioned recordings contain authoritative state for every received movement packet.
		boolean hasPerFramePhysicalState = frames.stream()
			.allMatch(frame -> frame.physicalPose() != null);
		Position initialPosition = Objects.requireNonNull(firstFrame.moveTo(), "initial position cannot be null");
		Rotation initialRotation = firstFrame.rotateTo() == null ? Rotation.zero() : firstFrame.rotateTo();
		AtomicReference<Location> currentLocation = new AtomicReference<>();
		World world = createReplayWorld();
		currentLocation.set(locationOf(world, initialPosition, initialRotation));

		MovementFrameState initialFrameState = firstFrame.movementState();
		boolean packetDrivenItemUse = recording.packetDrivenItemUse();
		boolean recordsActiveItemReplacements = recording.actions().stream()
			.anyMatch(ActiveItemReplacementAction.class::isInstance);
		ReplayPlayerState replayPlayerState = new ReplayPlayerState(
			initialFrameState == null ? MovementFrameState.empty() : initialFrameState,
			packetDrivenItemUse,
			packetDrivenItemUse && !recordsActiveItemReplacements
		);
		User user = createReplayUser(recording, blockCache, world, currentLocation, replayPlayerState);
		MovementMetadata metadata = user.meta().movement();
		if (initialFrameState != null) {
			replayPlayerState.applyInitial(user, initialFrameState);
		}
		applyAttributesForTick(recording, user, firstPositionFrame);
		metadata.gliding = firstFrame.gliding();
		seedInitialMovementState(user, metadata, initialPosition, initialRotation);
		applyRecordedGroundBeforeTick(metadata, firstFrame.clientOnGround());
		if (firstFrame.physicalPose() != null) {
			metadata.setPose(firstFrame.physicalPose());
		}

		// Replays validate item-use physics, including rejection of NoSlow recordings.
		ThreeTickSimulationSearch processor = new ThreeTickSimulationSearch(true);
		List<String> lastMessages = new LinkedList<>();

		for (int tick = firstPositionFrame + 1; tick < frames.size(); tick++) {
			MoveFrame frame = frames.get(tick);
			Input input = frame.input();

			MovementFrameState frameState = frame.movementState();
			if (frameState != null) {
				replayPlayerState.apply(user, frameState);
			}
			applyAttributesForTick(recording, user, tick);
			applyInputsForTick(user, input);
			applyActionsForTick(recording.actions(), user, tick);
			blockCache.updateBlocks(frame.blocks());
			if (isUseItemMovementSync(user, metadata, frame)) {
				user.meta().inventory().noteUseItemMovementSync();
				Rotation rotation = frame.rotateTo();
				metadata.setRotation(rotation.yaw(), rotation.pitch());
				continue;
			}

			Position position = frame.moveTo();
			Rotation rotation = frame.rotateTo();
			Location location = locationOf(
				world,
				position == null ? metadata.position() : position,
				rotation == null ? metadata.rotation() : rotation
			);
			currentLocation.set(location);

			boolean hasMovement = position != null;
			boolean hasRotation = rotation != null;
			metadata.gliding = frame.gliding();
			applyRecordedPoseBeforeTick(user, metadata, frame.physicalPose());
			metadata.updateMovement(
				location.getX(), location.getY(), location.getZ(),
				location.getYaw(), location.getPitch(),
				hasMovement, hasRotation
			);
			if (packetDrivenItemUse) {
				applyPacketDrivenItemUseBeforePhysics(user, hasMovement);
			}
			// The packet bit is the client state after its movement. The previous
			// frame is therefore the state entering this client tick.
			applyRecordedGroundBeforeTick(
				metadata, frames.get(tick - 1).clientOnGround()
			);
			Simulator simulator = Simulators.selectFor(metadata);
			metadata.setSimulator(simulator);
			metadata.stepHeight = simulator.stepHeight(user);
			metadata.treatThisFlyPacketAsMovePacket = false;
			if (!hasMovement && (!hasPerFramePhysicalState
				|| framesUntilNextPosition(frames, tick) <= 2)) {
				if (packetDrivenItemUse) {
					replayPlayerState.applyInferredActiveItemReplacement(user);
					user.meta().inventory().tickComplete();
				}
				continue;
			}

			Motion previousBaseMotion = metadata.mutableBaseMotionCopy();

			int precedingFlyingPackets = hasMovement && hasPerFramePhysicalState
				? Math.min(2, positionlessFramesBefore(frames, tick))
				: 0;
			if (precedingFlyingPackets > 0) {
				// An exact replay starts before the first omitted-position tick. Its
				// ground bit is the result of that tick, not the state entering it.
				applyRecordedGroundBeforeTick(
					metadata,
					frames.get(tick - precedingFlyingPackets - 1).clientOnGround()
				);
			}
			SimulationEnvironment searchEnvironment = metadata.mutableView();
			if (!hasMovement) {
				searchEnvironment.updateMovement(metadata.position(), metadata.rotation());
			}
			Simulation simulation;
			if (!hasPerFramePhysicalState) {
				simulation = processor.greedyFullTickSearch(user, searchEnvironment, simulator).simulation();
			} else if (hasMovement) {
				simulation = processor.exactFlyingPacketSearch(
					user, searchEnvironment, simulator, precedingFlyingPackets
				);
			} else {
				simulation = processor.positionlessFlyingPacketSearch(
					user, searchEnvironment, simulator
				);
			}
			if (hasPerFramePhysicalState && hasMovement && (simulation == Simulation.invalid()
				|| simulation.positionDifference(metadata.position()) > DIVERGED_MOTION_DISTANCE)) {
				Simulation genericSimulation = processor.greedyFullTickSearch(
					user, searchEnvironment, simulator
				).simulation();
				if (simulation == Simulation.invalid()
					|| genericSimulation.positionDifference(metadata.position())
					< simulation.positionDifference(metadata.position())) {
					simulation = genericSimulation;
					simulation.appendBlue("exact-fallback:" + precedingFlyingPackets + "f");
				}
			} else if (simulation == Simulation.invalid()) {
				simulation = processor.greedyFullTickSearch(
					user, searchEnvironment, simulator
				).simulation();
			}
			if (!hasMovement) {
				finishPositionlessTick(
					user, processor, simulator, metadata, simulation,
					previousBaseMotion, hasRotation, frame.clientOnGround(),
					packetDrivenItemUse, replayPlayerState
				);
				continue;
			}

			double loss = simulation.positionDifference(metadata.position());
			double allowedLoss = DIVERGED_MOTION_DISTANCE;
			observer.observe(tick, simulation, metadata.sentOffsetMotion());
			String output = formatDouble(loss, 4) + " " + simulation.offsetMotion() + " [actual: " + metadata.sentOffsetMotion() + "] " + simulation.configuration() + (!simulation.blueDetails().isEmpty() ? " [" + simulation.blueDetails() + "]" : "");
			lastMessages.add(formatSearchHistoryRow(tick, loss, simulation, metadata.sentOffsetMotion()));

//			System.out.println(loss);

			if (loss > allowedLoss && tick > 16) {
				System.out.println("\r" + "[FAILED] " + resourcePath + " (tick " + tick + ")");
				String failureReportLink = null;
				try {
					Path report = PtrBranchingVisualizationTest.writeFailureReport(
						resourcePath,
						recording,
						tick,
						simulation,
						metadata.sentOffsetMotion()
					);
					failureReportLink = PtrBranchingVisualizationTest.printReportLink(report, tick);
				} catch (Exception | AssertionError reportFailure) {
					System.out.println(
						"[REPORT] Unable to write tick " + tick + " report: "
							+ reportFailure.getMessage()
					);
				}
				printFailureReport(
					resourcePath, recording, frames, tick, loss, allowedLoss,
					lastMessages, blockCache, user, metadata, simulation, processor, simulator
				);
				fail("Movement diverged at tick " + tick + ": loss "
					+ formatDiagnosticDouble(loss) + " exceeds "
					+ formatDiagnosticDouble(allowedLoss)
					+ (failureReportLink == null ? "" : "\nPTR report: " + failureReportLink));
			}

			System.out.print("\r" + output);
			simulation.environment().commitTo(metadata);
			metadata.assumeOccurred(simulation);
			// Apply this packet's post-move state before deriving after-tick motion.
			applyRecordedGroundAfterMove(metadata, frame.clientOnGround());
			finishTick(
				user, processor, simulator, metadata,
				hasMovement, hasRotation, packetDrivenItemUse, replayPlayerState
			);

			if (lastMessages.size() > 16) {
				lastMessages.removeFirst();
			}
		}

		System.out.println("\r[SUCCESS] " + resourcePath + "...");
	}

	private static int framesUntilNextPosition(List<MoveFrame> frames, int tick) {
		for (int nextTick = tick + 1; nextTick < frames.size(); nextTick++) {
			if (frames.get(nextTick).moveTo() != null) {
				return nextTick - tick;
			}
		}
		return 0;
	}

	private static int positionlessFramesBefore(List<MoveFrame> frames, int tick) {
		int count = 0;
		for (int previousTick = tick - 1;
			 previousTick >= 0 && frames.get(previousTick).moveTo() == null;
			 previousTick--
		) {
			count++;
		}
		return count;
	}

	private static void finishPositionlessTick(
		User user,
		SimulationSearch processor,
		Simulator simulator,
		MovementMetadata metadata,
		Simulation simulation,
		Motion previousBaseMotion,
		boolean hasRotation,
		Boolean clientOnGround,
		boolean packetDrivenItemUse,
		ReplayPlayerState replayPlayerState
	) {
		boolean reinterpretedAsMove = simulation.environment().tryMoveReinterpretation(
			simulation,
			user.meta().protocol().flyingPacketUncertaintyRadius()
		);
		Position recordedPosition = metadata.position();
		BoundingBox recordedBounds = metadata.boundingBox();
		if (reinterpretedAsMove) {
			simulation.environment().commitTo(metadata);
			metadata.assumeOccurred(simulation);
		} else {
			metadata.setSwimming(simulation.environment().isSwimming());
			metadata.setLastMovementConfiguration(simulation.configuration());
			List<PostTickSimulation> advancedCandidates = new ArrayList<>();
			for (PostTickSimulation candidate : metadata.postTickMotionCandidates()) {
				advancedCandidates.add(new PostTickSimulation(
					candidate.motion(), simulation.configuration().isSprinting()
				));
			}
			metadata.setPostTickMotionCandidates(advancedCandidates);
			metadata.setBaseMotion(previousBaseMotion);
			updateOnGroundIfFlying(user, metadata);
		}
		applyRecordedGroundAfterMove(metadata, clientOnGround);
		finishTick(
			user, processor, simulator, metadata,
			false, hasRotation, packetDrivenItemUse, replayPlayerState
		);
		if (reinterpretedAsMove) {
			metadata.setPosition(recordedPosition);
			metadata.setLastPosition(recordedPosition);
			metadata.setVerifiedLastPosition(recordedPosition, "positionless recording frame");
			metadata.setBoundingBox(recordedBounds);
		}
	}

	private static void updateOnGroundIfFlying(User user, MovementMetadata metadata) {
		double physicsMotionX = Math.abs(metadata.baseMotionX) < metadata.resetMotion()
			? 0.0D : metadata.baseMotionX;
		double physicsMotionY = Math.abs(metadata.baseMotionY) < metadata.resetMotion()
			? 0.0D : metadata.baseMotionY;
		double physicsMotionZ = Math.abs(metadata.baseMotionZ) < metadata.resetMotion()
			? 0.0D : metadata.baseMotionZ;
		metadata.onGround = Colliders.simplifiedCollision(
			user.player(), metadata,
			metadata.verifiedLastPositionX,
			metadata.verifiedLastPositionY,
			metadata.verifiedLastPositionZ,
			physicsMotionX * 0.91F,
			(physicsMotionY - 0.08D) * 0.98F,
			physicsMotionZ * 0.91F
		).onGround();
	}

	private static void printFailureReport(
		String resourcePath,
		MovementRecording recording,
		List<MoveFrame> frames,
		int tick,
		double loss,
		double allowedLoss,
		List<String> recentSearchHistory,
		PlaybackBlockCacheView blockCache,
		User user,
		MovementMetadata metadata,
		Simulation simulation,
		SimulationSearch processor,
		Simulator simulator
	) {
		System.err.println();
		System.err.println("================================================================================");
		System.err.println("MOVEMENT PHYSICS FAILURE");
		diagnosticLine("Recording", resourcePath);
		diagnosticLine("Tick", tick + " / " + (frames.size() - 1));
		diagnosticLine("Versions", "client protocol " + recording.clientProtocolVersion()
			+ ", server " + recording.serverVersion());
		diagnosticLine("Position loss", formatDiagnosticDouble(loss));
		diagnosticLine("Allowed loss", formatDiagnosticDouble(allowedLoss));
		diagnosticLine("Excess", formatDiagnosticDouble(loss - allowedLoss));
		System.err.println("================================================================================");

		Position predictedPosition = simulation.predictedPosition();
		Position recordedPosition = metadata.position();
		Motion predictedMotion = simulation.offsetMotion();
		Motion recordedMotion = metadata.sentOffsetMotion();

		printSection("Mismatch summary");
		diagnosticLine("Predicted position", formatPosition(predictedPosition));
		diagnosticLine("Recorded position", formatPosition(recordedPosition));
		diagnosticLine("Position delta", formatMotion(predictedPosition.motionTo(recordedPosition))
			+ "  (recorded - predicted)");
		diagnosticLine("Predicted motion", formatMotion(predictedMotion));
		diagnosticLine("Recorded motion", formatMotion(recordedMotion));
		diagnosticLine("Motion delta", formatMotion(motionDifference(recordedMotion, predictedMotion))
			+ "  (recorded - predicted)");

		printSection("Tick state");
		diagnosticLine("Pressed inputs", formatInput(metadata.input));
		diagnosticLine("Rotation", formatRotation(metadata.rotation()));
		diagnosticLine("Previous position", formatPosition(metadata.lastPosition()));
		diagnosticLine("Verified position", formatPosition(metadata.verifiedLastPosition()));
		Position nextPosition = nextRecordedPosition(frames, tick);
		if (nextPosition != null) {
			diagnosticLine("Next position", formatPosition(nextPosition));
			diagnosticLine("Next Y delta", formatDiagnosticDouble(
				nextPosition.getY() - recordedPosition.getY()
			));
		}
		diagnosticLine("Base motion", formatMotion(metadata.mutableBaseMotionCopy()));
		diagnosticLine("Ground state", "current=" + metadata.onGround()
			+ ", previous=" + metadata.lastOnGround());
		diagnosticLine(
			"Recorded ground",
			"previous=" + formatRecordedGround(tick > 0
				? frames.get(tick - 1).clientOnGround()
				: null)
				+ ", current=" + formatRecordedGround(frames.get(tick).clientOnGround())
		);
		diagnosticLine("Gliding", metadata.gliding);
		diagnosticLine("Pose", metadata.pose());
		diagnosticLine(
			"Swimming state",
			"stored=" + metadata.isSwimming()
				+ ", selected=" + simulation.environment().isSwimming()
				+ ", eyesInWater=" + metadata.areEyesInWater()
		);
		diagnosticLine("Simulator", simulator.getClass().getSimpleName());
		diagnosticLine("Sneaking", metadata.isSneaking());
		diagnosticLine(
			"Block movement state",
			"collide=" + metadata.collideMaterial()
				+ ", friction=" + metadata.frictionMaterial()
				+ ", speedFactor=" + formatDiagnosticDouble(metadata.blockSpeedFactor())
				+ ", supporting=" + metadata.mainSupportingBlockPos()
		);
		diagnosticLine("Motion multiplier", String.valueOf(metadata.motionMultiplier()));
		diagnosticLine("Ability scale", formatDiagnosticDouble(user.meta().abilities().scale()));
		diagnosticLine("Player bounds", formatBoundingBox(metadata.boundingBox()));

		printSection("Selected simulation");
		printSimulationDetails(simulation, recordedPosition);
		printMotionCandidates(metadata.postTickMotionCandidates());

		printAttributes(recording.attributesForFrame(tick));
		printSearchHistory(recentSearchHistory);
		printFrameTimeline(frames, tick);
		printClosestSimulations(processor, user, metadata, simulator);

		if (metadata.input.jumpKey()) {
			Simulation forcedJump = simulator.simulateTick(
				user,
				metadata.mutableBaseMotionCopy(),
				metadata.mutableView(),
				simulation.configuration().withJump()
			);
			printSection("Forced-jump comparison");
			printSimulationDetails(forcedJump, recordedPosition);
		}

		printCollisionSweep(blockCache, metadata, simulation);
		System.err.println();
		System.err.println("================================================================================");
		System.err.println("END MOVEMENT PHYSICS FAILURE");
		System.err.println("================================================================================");
	}

	private static String formatSearchHistoryRow(
		int tick,
		double loss,
		Simulation simulation,
		Motion recordedMotion
	) {
		return String.format(
			Locale.ROOT,
			"  tick %d  loss=%s  config=%s%s%n"
				+ "    predicted=%s%n"
				+ "    recorded =%s",
			tick,
			formatDiagnosticDouble(loss),
			simulation.configuration(),
			simulation.blueDetails().isEmpty() ? "" : "  details=" + simulation.blueDetails(),
			formatMotion(simulation.offsetMotion()),
			formatMotion(recordedMotion)
		);
	}

	private static void printSearchHistory(List<String> recentSearchHistory) {
		printSection("Recent search history (oldest to newest)");
		for (String row : recentSearchHistory) {
			System.err.println(row);
		}
	}

	private static Position nextRecordedPosition(List<MoveFrame> frames, int tick) {
		if (tick + 1 >= frames.size()) {
			return null;
		}
		return frames.get(tick + 1).moveTo();
	}

	private static void printSimulationDetails(Simulation simulation, Position recordedPosition) {
		var result = simulation.result();
		diagnosticLine("Loss", formatDiagnosticDouble(simulation.positionDifference(recordedPosition)));
		diagnosticLine("Configuration", simulation.configuration());
		diagnosticLine("Resolved motion", formatMotion(result.offsetMotion()));
		diagnosticLine("Attempted motion", formatNullableMotion(result.actualMotion()));
		diagnosticLine("Intermediate motion", formatNullableMotion(result.intermittentResult()));
		diagnosticLine("Collision", "horizontal=" + result.collidedHorizontally()
			+ ", vertical=" + result.collidedVertically()
			+ ", onGround=" + result.onGround());
		diagnosticLine("Motion reset", "x=" + result.resetMotionX() + ", z=" + result.resetMotionZ());
		diagnosticLine("Special handling", "step=" + result.step()
			+ ", edgeSneak=" + result.edgeSneak()
			+ ", stepHeight=" + formatDiagnosticDouble(result.stepHeightThisMove()));
		if (!simulation.blueDetails().isEmpty()) {
			diagnosticLine("Search details", simulation.blueDetails());
		}
	}

	private static void printMotionCandidates(List<PostTickSimulation> candidates) {
		diagnosticLine("Base candidates", candidates.size());
		for (int index = 0; index < candidates.size(); index++) {
			PostTickSimulation candidate = candidates.get(index);
			diagnosticLine(
				"  candidate " + (index + 1),
				formatMotion(candidate.motion()) + (candidate.priorSprinting() ? " _SPR" : "")
			);
		}
	}

	private static void printAttributes(Map<String, Attribute> attributes) {
		printSection("Attributes");
		if (attributes.isEmpty()) {
			System.err.println("  <none recorded>");
			return;
		}
		attributes.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> diagnosticLine(
				entry.getKey(),
				formatDiagnosticDouble(entry.getValue().baseValue())
					+ (entry.getValue().modifiers().isEmpty()
					? ""
					: "  modifiers=" + entry.getValue().modifiers().size())
			));
	}

	private static void applyRecordedGroundBeforeTick(
		MovementMetadata metadata, Boolean clientOnGround
	) {
		if (clientOnGround == null) {
			return;
		}
		metadata.onGround = clientOnGround;
		metadata.lastOnGround = clientOnGround;
	}

	private static void applyRecordedGroundAfterMove(
		MovementMetadata metadata, Boolean clientOnGround
	) {
		if (clientOnGround != null) {
			metadata.onGround = clientOnGround;
		}
	}

	private static String formatRecordedGround(Boolean clientOnGround) {
		return clientOnGround == null ? "not recorded" : clientOnGround.toString();
	}

	private static void preparePhysicsTestRuntime(MovementRecording recording) {
		ReplayPotionRegistry.initialize();
		MinecraftVersion serverVersion = recording.serverVersion();
		MinecraftVersion.setCurrent(serverVersion);
		ItemProperties.setup();
		DrillResolver.manualInit(DenyShapeResolverPipeline.create());
		Fluids.overrideFluids(recording.fluids());
		BlockVariantRegister.overrideVariants(recording.blockVariants());
		BlockPhysics.setup(serverVersion);
	}

	private static User createReplayUser(
		MovementRecording recording,
		PlaybackBlockCacheView blockCache, World world,
		AtomicReference<Location> currentLocation
	) {
		return createReplayUser(
			recording, blockCache, world, currentLocation,
			new ReplayPlayerState(MovementFrameState.empty())
		);
	}

	private static User createReplayUser(
		MovementRecording recording,
		PlaybackBlockCacheView blockCache, World world,
		AtomicReference<Location> currentLocation,
		ReplayPlayerState replayPlayerState
	) {
		UUID replayUserId = UUID.randomUUID();
		Player player = FakePlayerFactory.createPlayer(
			(methodName, _) -> switch (methodName) {
				case "getWorld" -> world;
				case "getLocation" -> currentLocation.get().clone();
				case "getUniqueId" -> replayUserId;
				case "getInventory" -> replayPlayerState.inventory();
				case "getActivePotionEffects" -> replayPlayerState.state().effects().activePotionEffects();
				case "isFlying" -> replayPlayerState.state().abilities().flying();
				case "getAllowFlight" -> replayPlayerState.state().abilities().allowFlying();
				case "getFlySpeed" -> replayPlayerState.state().abilities().flySpeed() * 2.0F;
				case "getGameMode" -> replayPlayerState.state().abilities().gameMode();
				case "isOnGround", "isDead" -> false;
				default -> null;
			}
		);

		int protocolVersion = recording.clientProtocolVersion();
		User user = UserFactory.createTestUserFor(player, (usr, key) -> switch (key) {
			case "blockCache" -> blockCache;
			case "trustFactor" -> TrustFactor.RED;
			case "justJoined" -> false;
			case "joined" -> 0L;
			case "latency", "latencyJitter" -> 0;
			case "shouldIgnoreNextInboundPacket", "shouldIgnoreNextOutboundPacket" -> false;
			case "protocolVersion" -> protocolVersion;
			default -> null;
		});
		UserRepository.manuallyRegisterUser(player, user);
		return user;
	}

	private static final class ReplayPlayerState {
		private final AtomicReference<MovementFrameState> state;
		private final MockEmptyInventory inventory = new MockEmptyInventory();
		private final boolean packetDrivenItemUse;
		private final boolean inferActiveItemReplacements;
		private Material inferredActiveItemReplacement;
		private boolean initialStateApplied;

		private ReplayPlayerState(MovementFrameState initialState) {
			this(initialState, false, false);
		}

		private ReplayPlayerState(
			MovementFrameState initialState, boolean packetDrivenItemUse
		) {
			this(initialState, packetDrivenItemUse, false);
		}

		private ReplayPlayerState(
			MovementFrameState initialState,
			boolean packetDrivenItemUse,
			boolean inferActiveItemReplacements
		) {
			this.state = new AtomicReference<>(initialState);
			this.packetDrivenItemUse = packetDrivenItemUse;
			this.inferActiveItemReplacements = inferActiveItemReplacements;
		}

		private MovementFrameState state() {
			return state.get();
		}

		private MockEmptyInventory inventory() {
			return inventory;
		}

		private void apply(User user, MovementFrameState nextState) {
			MovementFrameState previousState = state.get();
			if (inferActiveItemReplacements && initialStateApplied
				&& nextState.inventory().selectedMainHandItemChangedFrom(
					previousState.inventory()
				)) {
				MovementFrameState.ItemState replacement = nextState.inventory().heldItem();
				inferredActiveItemReplacement = replacement == null
					? Material.AIR : replacement.material();
			}
			state.set(nextState);
			if (packetDrivenItemUse && initialStateApplied) {
				nextState.applyToPreservingItemUsePackets(user, inventory);
			} else {
				nextState.applyTo(user, inventory);
			}
			initialStateApplied = true;
		}

		private void applyInferredActiveItemReplacement(User user) {
			Material replacementType = inferredActiveItemReplacement;
			inferredActiveItemReplacement = null;
			InventoryMetadata inventoryMetadata = user.meta().inventory();
			if (replacementType == null || !inventoryMetadata.handActive()
				|| inventoryMetadata.activeHand() != PlayerHand.MAIN_HAND) {
				return;
			}
			inventoryMetadata.applyClientActiveItemReplacement(
				inventoryMetadata.activeItemUseSequence(), replacementType
			);
		}

		private void applyInitial(User user, MovementFrameState nextState) {
			state.set(nextState);
			nextState.applyTo(user, inventory);
			initialStateApplied = true;
		}

		private boolean packetDrivenItemUse() {
			return packetDrivenItemUse;
		}
	}

	private static World createReplayWorld() {
		return FakeWorldFactory.createWorld(
			(methodName, _) -> switch (methodName) {
				case "isChunkLoaded", "isChunkInUse" -> true;
				case "isThundering", "hasStorm" -> false;
				default -> null;
			}
		);
	}

	private static void seedInitialMovementState(
		User user,
		MovementMetadata metadata,
		Position initialPosition,
		Rotation initialRotation
	) {
		metadata.updateMovement(initialPosition, initialRotation);
		metadata.setVerifiedLastPosition(initialPosition, "recording seed");
		metadata.setLastPosition(initialPosition);
		metadata.setBaseMotion(Motion.newEmpty());
		metadata.setBoundingBox(BoundingBox.fromPosition(user, metadata, initialPosition));
		metadata.compileSpecialBlocks();
		metadata.onGround = Colliders.simplifiedCollision(
			user.player(), metadata,
			initialPosition.getX(), initialPosition.getY(), initialPosition.getZ(),
			0.0D, -0.01D, 0.0D
		).onGround();
		metadata.lastOnGround = metadata.onGround;
	}

	private static void finishTick(
		User user,
		SimulationSearch processor,
		Simulator simulator,
		MovementMetadata metadata,
		boolean hasMovement,
		boolean hasRotation,
		boolean packetDrivenItemUse,
		ReplayPlayerState replayPlayerState
	) {
		if (hasMovement) {
			metadata.setPostTickMotionCandidates(
				processor.afterTickMotionCandidates(
					user, metadata, simulator,
					metadata.position(),
					SENT_OFFSET_MOTION
				)
			);
			metadata.inactiveTick(
				FLYING_PACKET_ACCURATE,
				FLYING_PACKET_CLIENT,
				NEARBY_COLLISION_INACCURACY,
				ENTITY_USE
			);
		} else if (hasRotation || metadata.treatThisFlyPacketAsMovePacket) {
			metadata.setPostTickMotionCandidates(
				processor.afterTickMotionCandidates(
					user, metadata, simulator,
					metadata.lastPosition().mutable().add(metadata.sentOffsetMotion()),
					SENT_OFFSET_MOTION
				)
			);
		}

		metadata.tickComplete(hasMovement, hasRotation, true);
		if (packetDrivenItemUse) {
			replayPlayerState.applyInferredActiveItemReplacement(user);
			user.meta().inventory().tickComplete();
		}

		metadata.lastKeyStrafe = metadata.keyStrafe;
		metadata.lastKeyForward = metadata.keyForward;
		metadata.lastOnGround = metadata.onGround;
		metadata.setVerifiedLastPosition(metadata.position(), "recording replay");
	}

	private static void printCollisionSweep(
		PlaybackBlockCacheView blockCache,
		MovementMetadata metadata,
		Simulation simulation
	) {
		Motion attemptedMotion = simulation.result().actualMotion();
		if (attemptedMotion == null) {
			attemptedMotion = simulation.offsetMotion();
		}
		Motion diagnosticMotion = attemptedMotion.copy();
		diagnosticMotion.motionY = Math.max(diagnosticMotion.motionY, metadata.jumpMotion());
		BoundingBox sweep = metadata.boundingBox().expand(diagnosticMotion);
		printSection("Collision sweep");
		diagnosticLine("Swept bounds", formatBoundingBox(sweep));
		int minX = (int) Math.floor(sweep.minX);
		int maxX = (int) Math.floor(sweep.maxX);
		int minY = (int) Math.floor(sweep.minY) - 1;
		int maxY = (int) Math.floor(sweep.maxY);
		int minZ = (int) Math.floor(sweep.minZ);
		int maxZ = (int) Math.floor(sweep.maxZ);
		int printLimit = 128;
		List<String> blocks = new ArrayList<>();
		boolean truncated = false;
		scan:
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					BlockState state = blockCache.stateAt(x, y, z);
					if (state.type() == org.bukkit.Material.AIR) {
						continue;
					}
					if (blocks.size() >= printLimit) {
						truncated = true;
						break scan;
					}
					blocks.add("  (" + x + ", " + y + ", " + z + ") "
						+ state.type() + "#" + state.variantIndex()
						+ " properties=" + state.properties()
						+ System.lineSeparator()
						+ "      shape=" + formatBoundingBoxes(state.collisionShape().elementaryBoxes()));
				}
			}
		}
		diagnosticLine("Non-air blocks", blocks.size() + (truncated ? "+" : ""));
		for (String block : blocks) {
			System.err.println(block);
		}
		if (blocks.isEmpty()) {
			System.err.println("  <no non-air blocks>");
		} else if (truncated) {
			System.err.println("  ... output truncated after " + printLimit + " blocks");
		}
	}

	private static void printFrameTimeline(List<MoveFrame> frames, int failingTick) {
		printSection("Recorded frame timeline");
		System.err.println("  \"-\" means the packet did not contain that field");
		int from = Math.max(0, failingTick - 5);
		int to = Math.min(frames.size() - 1, failingTick + 2);
		for (int tick = from; tick <= to; tick++) {
			MoveFrame frame = frames.get(tick);
			System.err.println(String.format(
				Locale.ROOT,
				"  %s tick %d  position=%s  input=%s%n"
					+ "      rotation=%s  gliding=%s  physicalPose=%s  clientOnGround=%s  blockChanges=%d",
				tick == failingTick ? ">" : " ",
				tick,
				frame.moveTo() == null ? "-" : formatPosition(frame.moveTo()),
				formatInput(frame.input()),
				frame.rotateTo() == null ? "-" : formatRotation(frame.rotateTo()),
				frame.gliding(),
				frame.physicalPose() == null ? "-" : frame.physicalPose(),
				formatRecordedGround(frame.clientOnGround()),
				frame.blocks().size()
			));
		}
	}

	private static void printClosestSimulations(
		SimulationSearch processor,
		User user,
		MovementMetadata metadata,
		Simulator simulator
	) {
		printSection("Closest exhaustive simulations");
		List<Simulation> candidates = processor.exhaustiveTickSearch(
				user, metadata.mutableView(), simulator
			).stream()
			.sorted(Comparator.comparingDouble(simulation -> simulation.positionDifference(metadata.position())))
			.limit(10)
			.collect(Collectors.toList());
		if (candidates.isEmpty()) {
			System.err.println("  <no simulations>");
			return;
		}
		for (int index = 0; index < candidates.size(); index++) {
			Simulation candidate = candidates.get(index);
			System.err.println(String.format(
				Locale.ROOT,
				"  #%02d  loss=%s  config=%s%n"
					+ "       motion=%s  flags=%s",
				index + 1,
				formatDiagnosticDouble(candidate.positionDifference(metadata.position())),
				candidate.configuration(),
				formatMotion(candidate.offsetMotion()),
				formatResultFlags(candidate)
			));
		}
	}

	private static String formatResultFlags(Simulation simulation) {
		var result = simulation.result();
		List<String> flags = new ArrayList<>();
		if (result.onGround()) flags.add("ground");
		if (result.collidedHorizontally()) flags.add("collision-horizontal");
		if (result.collidedVertically()) flags.add("collision-vertical");
		if (result.resetMotionX()) flags.add("reset-x");
		if (result.resetMotionZ()) flags.add("reset-z");
		if (result.step()) flags.add("step");
		if (result.edgeSneak()) flags.add("edge-sneak");
		return flags.isEmpty() ? "none" : String.join(",", flags);
	}

	private static String formatInput(Input input) {
		List<String> keys = new ArrayList<>();
		if (input.forwardKey()) keys.add("forward");
		if (input.backwardKey()) keys.add("backward");
		if (input.leftKey()) keys.add("left");
		if (input.rightKey()) keys.add("right");
		if (input.jumpKey()) keys.add("jump");
		if (input.sneakKey()) keys.add("sneak");
		if (input.sprintKey()) keys.add("sprint");
		return keys.isEmpty() ? "<none>" : String.join("+", keys);
	}

	private static String formatPosition(Position position) {
		return String.format(
			Locale.ROOT,
			"(%.6f, %.6f, %.6f)",
			position.getX(), position.getY(), position.getZ()
		);
	}

	private static String formatMotion(Motion motion) {
		return String.format(
			Locale.ROOT,
			"(%.6f, %.6f, %.6f)",
			motion.motionX(), motion.motionY(), motion.motionZ()
		);
	}

	private static String formatNullableMotion(Motion motion) {
		return motion == null ? "<not recorded>" : formatMotion(motion);
	}

	private static String formatRotation(Rotation rotation) {
		return String.format(
			Locale.ROOT,
			"yaw=%.6f, pitch=%.6f",
			rotation.yaw(), rotation.pitch()
		);
	}

	private static String formatBoundingBox(BoundingBox box) {
		return String.format(
			Locale.ROOT,
			"min=(%.6f, %.6f, %.6f), max=(%.6f, %.6f, %.6f)",
			box.minX, box.minY, box.minZ,
			box.maxX, box.maxY, box.maxZ
		);
	}

	private static String formatBoundingBoxes(List<BoundingBox> boxes) {
		if (boxes.isEmpty()) {
			return "<empty>";
		}
		return boxes.stream()
			.map(MovementRecordingPhysicsTests::formatBoundingBox)
			.collect(Collectors.joining("; "));
	}

	private static Motion motionDifference(Motion first, Motion second) {
		return new Motion(
			first.motionX() - second.motionX(),
			first.motionY() - second.motionY(),
			first.motionZ() - second.motionZ()
		);
	}

	private static String formatDiagnosticDouble(double value) {
		return String.format(Locale.ROOT, "%.9f", value);
	}

	private static void printSection(String title) {
		System.err.println();
		System.err.println("[ " + title.toUpperCase(Locale.ROOT) + " ]");
	}

	private static void diagnosticLine(String label, Object value) {
		System.err.println(String.format(Locale.ROOT, "  %-22s %s", label + ":", value));
	}

	private static void applyActionsForTick(
		List<Action> actions,
		User user,
		int tick
	) {
		MovementMetadata metadata = user.meta().movement();
		int recordedAttackReductions = 0;
		for (Action action : actions) {
			if (action instanceof ReceiveVelocity velocity) {
				if (velocity.tickRange().start() == tick) {
					Motion motion = velocity.motion();
					long duration = velocity.tickRange().end() - velocity.tickRange().start();
					if (duration <= 0) {
						throw new IllegalStateException("Invalid velocity tick range: " + velocity.tickRange());
					}
					// Live velocity packets are applied by UpdateBrancher after
					// retained post-tick motion candidates have been selected.
					MotionUpdate update = MotionUpdate.replacement(motion, metadata);
					update.setRunNotAfter(metadata.currentTick() + duration - 1);
					metadata.queueTickAmbiguousUpdate(update);
					metadata.baseMotionXBeforeVelocity = metadata.baseMotionX;
					metadata.baseMotionYBeforeVelocity = metadata.baseMotionY;
					metadata.baseMotionZBeforeVelocity = metadata.baseMotionZ;
					metadata.lastVelocity = motion.copy();
					metadata.activeTick(EXTERNAL_VELOCITY);
					metadata.activeTick(RECEIVED_VELOCITY_PACKET);
					metadata.activeTick(VELOCITY);
				}
			} else if (action instanceof PistonSlimeAction piston) {
				if (piston.tickRange().start() == tick) {
					long duration = piston.tickRange().end() - piston.tickRange().start();
					if (duration <= 0) {
						throw new IllegalStateException("Invalid piston tick range: " + piston.tickRange());
					}
					PistonActionUpdate update = PistonActionUpdate.openEnded(
						piston.direction(), piston.slimeSources(), metadata
					);
					update.setRunNotAfter(metadata.currentTick() + duration - 1);
					metadata.queueTickAmbiguousUpdate(update);
				}
			} else if (action instanceof ShulkerBoxAction shulker) {
				if (shulker.tickRange().start() == tick) {
					long duration = shulker.tickRange().end() - shulker.tickRange().start();
					if (duration <= 0) {
						throw new IllegalStateException("Invalid shulker-box tick range: " + shulker.tickRange());
					}
					ShulkerBoxActionUpdate update = ShulkerBoxActionUpdate.openEnded(
						shulker.position(), shulker.direction(), shulker.opening(), metadata
					);
					update.setRunNotAfter(metadata.currentTick() + duration - 1);
					metadata.queueTickAmbiguousUpdate(update);
				}
			} else if (action instanceof AttackReduction reduction) {
				if (reduction.tickRange().start() == tick) {
					long duration = reduction.tickRange().end() - reduction.tickRange().start();
					if (duration != 1) {
						throw new IllegalStateException(
							"Invalid attack-reduction tick range: " + reduction.tickRange()
						);
					}
					metadata.queueTickAmbiguousUpdate(Reduce.openEnded(
						reduction.lastActiveInTick(), reduction.mandatory(), metadata
					));
					metadata.activeTick(ATTACK_REDUCE);
					recordedAttackReductions++;
				}
			} else if (action instanceof ItemUsePacketAction itemUse
				&& itemUse.tickRange().start() == tick) {
				applyItemUsePacket(user, itemUse);
			} else if (action instanceof ActiveItemReplacementAction replacement
				&& replacement.tickRange().start() == tick) {
				InventoryMetadata inventory = user.meta().inventory();
				inventory.applyClientActiveItemReplacement(
					inventory.activeItemUseSequence(), replacement.replacementType()
				);
			}
		}
		if (recordedAttackReductions > 0) {
			metadata.restoreRecordedCombatState(
				Math.max(metadata.reduceTicks(), recordedAttackReductions),
				0,
				metadata.ticksPast(ENTITY_USE)
			);
		}
	}

	private static void applyInputsForTick(
		User user, Input input
	) {
		MovementMetadata movement = user.meta().movement();
		if (user.meta().protocol().sendsInputs() && MinecraftVersions.VER1_21_3.atOrAbove()) {
			movement.lastInput = movement.input;
			movement.input = input;
		}
		boolean sprinting = user.meta().protocol().sendsInputs()
			? input.sprintKey() || movement.sprinting && input.forwardKey()
			: input.sprintKey();
		if (movement.sprinting != sprinting) {
			movement.activeTick(SPRINT_CHANGE);
		}
		movement.lastSneaking = movement.sneaking;
		movement.sneaking = input.sneakKey();
		movement.sprinting = sprinting;
	}

	private static boolean isUseItemMovementSync(
		User user,
		MovementMetadata metadata,
		MoveFrame frame
	) {
		Position position = frame.moveTo();
		// MultiPlayerGameMode#useItem sends the player's current position and rotation
		// immediately before the use-item packet. ViaVersion can cancel the paired
		// use packet while forwarding this synchronization packet. MovementDispatcher
		// drops every such duplicate, so it must not advance replay physics either.
		return user.meta().protocol().useItemMovementPacket()
			&& position != null
			&& frame.rotateTo() != null
			&& position.distance(metadata.verifiedLastPosition()) < 0.00001D;
	}

	private static void applyItemUsePacket(
		User user, ItemUsePacketAction action
	) {
		InventoryMetadata inventory = user.meta().inventory();
		switch (action.packet()) {
			case USE_MAIN_HAND:
				inventory.activateHandIfUsable(PlayerHand.MAIN_HAND);
				break;
			case USE_OFF_HAND:
				inventory.activateHandIfUsable(PlayerHand.OFF_HAND);
				break;
			case RELEASE_USE_ITEM:
				inventory.clearViaVersionItemUseStateUncertainty();
				inventory.deactivateHand();
				break;
			case HELD_ITEM_SLOT:
				int slot = action.slot();
				inventory.receiveHeldItemSlot(
					slot, user.player().getInventory().getItem(slot)
				);
				break;
		}
	}

	private static void applyPacketDrivenItemUseBeforePhysics(
		User user, boolean hasMovement
	) {
		InventoryMetadata inventory = user.meta().inventory();
		inventory.updateSlotSwitch();
		inventory.deferScheduledItemReleaseUntilTickEnd(hasMovement);
		inventory.clearItemUseTickTransitions();
	}

	private static void applyRecordedPoseBeforeTick(
		User user,
		MovementMetadata metadata,
		Pose recordedPose
	) {
		if (recordedPose == null) {
			return;
		}
		// A recorded pose is Intave's derived state, not a client-authored field. Old recordings can
		// therefore contain the former active-flight crouch bug. Preserve the replay's end-of-tick
		// pose here so the client's collision fallback remains ordered and stateful.
		if (user.protocolVersion() >= VER_1_14
			&& metadata.flying()
			&& recordedPose == Pose.CROUCHING) {
			return;
		}
		metadata.setPose(recordedPose);
	}

	private static void applyAttributesForTick(
		MovementRecording recording,
		User user,
		int tick
	) {
		var attributes = recording.attributesForFrame(tick);
		if (!attributes.isEmpty()) {
			var abilities = user.meta().abilities();
			abilities.replaceAttributeSnapshot(attributes);
			var movementSpeed = abilities.findAttribute("generic.movementSpeed");
			user.meta().movement().hasSprintSpeed = movementSpeed != null
				&& AbilityMetadata.hasSprintModifier(abilities.modifiersOf(movementSpeed));
		}
	}

	private static int firstPositionFrame(List<MoveFrame> frames) {
		for (int i = 0; i < frames.size(); i++) {
			if (frames.get(i).moveTo() != null) {
				return i;
			}
		}
		return -1;
	}

	private static Location locationOf(
		World world,
		Position position,
		Rotation rotation
	) {
		Location location = position.toLocation(world);
		location.setYaw(rotation.yaw());
		location.setPitch(rotation.pitch());
		return location;
	}

	static List<Path> findMovementRecordings() throws IOException {
		return findMovementRecordings(Paths.get("src", "test", "resources", "physics_test_runs"));
	}

	static List<Path> findMovementRecordings(Path recordingRoot) throws IOException {
		try (Stream<Path> paths = Files.walk(recordingRoot)) {
			return paths
				.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".ptr"))
				.sorted()
				.collect(Collectors.toList());
		} catch (NoSuchFileException ignored) {
			return List.of();
		}
	}

	static String resourcePathOf(Path recordingPath) {
		Path resourcesRoot = Paths.get("src", "test", "resources");
		return resourcesRoot.relativize(recordingPath).toString().replace('\\', '/');
	}
}
