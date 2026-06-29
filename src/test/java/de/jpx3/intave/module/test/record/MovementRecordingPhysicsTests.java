package de.jpx3.intave.module.test.record;

import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.PlaybackBlockCacheView;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.shape.resolve.DenyShapeResolverPipeline;
import de.jpx3.intave.block.shape.resolve.DrillResolver;
import de.jpx3.intave.check.movement.physics.*;
import de.jpx3.intave.module.test.record.action.Action;
import de.jpx3.intave.module.test.record.action.ReceiveVelocity;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.share.*;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.world.border.MockWorldBorder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.jpx3.intave.check.movement.physics.MoveMetric.*;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static org.junit.jupiter.api.Assertions.*;

final class MovementRecordingPhysicsTests {
	private static final UUID EMPTY_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
	private static final double DIVERGED_MOTION_DISTANCE = 0.5;

	@Test
	void simulationProcessorProcessesAllRecordedMovements() throws IOException {
		List<Path> recordingPaths = findMovementRecordings();
		assertFalse(recordingPaths.isEmpty(), "No movement recordings were found");

		for (Path recordingPath : recordingPaths) {
			String resourcePath = resourcePathOf(recordingPath);
			MovementRecording recording = MovementRecording.loadFrom(
				Resources.resourceFromJarOrTestBuild(resourcePath)
			);
			preparePhysicsTestRuntime(recording);
			processRecording(resourcePath, recording);
		}
	}

	private static void processRecording(
		String resourcePath,
		MovementRecording recording
	) {
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
		Position initialPosition = Objects.requireNonNull(firstFrame.moveTo(), "initial position cannot be null");
		Rotation initialRotation = firstFrame.rotateTo() == null ? Rotation.zero() : firstFrame.rotateTo();
		AtomicReference<Location> currentLocation = new AtomicReference<>();
		World world = createReplayWorld();
		currentLocation.set(locationOf(world, initialPosition, initialRotation));

		User user = createReplayUser(recording, blockCache, world, currentLocation);
		MovementMetadata metadata = user.meta().movement();
		seedInitialMovementState(user, metadata, initialPosition, initialRotation);

		SimulationProcessor processor = new PredictiveSimulationProcessor(false, false);
		Simulator simulator = Simulators.PLAYER;

		for (int tick = firstPositionFrame + 1; tick < frames.size(); tick++) {
			MoveFrame frame = frames.get(tick);
			Input input = frame.input();

			applyInputsForTick(user, input);
			applyActionsForTick(recording.actions(), metadata, tick);
			blockCache.updateBlocks(frame.blocks());

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
			metadata.updateMovement(
				location.getX(), location.getY(), location.getZ(),
				location.getYaw(), location.getPitch(),
				hasMovement, hasRotation
			);
			metadata.setSimulator(simulator);
			metadata.stepHeight = simulator.stepHeight();

			Motion baseMotion = metadata.mutableBaseMotionCopy();
			simulator.simulatePreTick(user, baseMotion, metadata);
			metadata.setBaseMotion(baseMotion);

			Simulation simulation = processor.simulate(user, simulator);
			double accuracy = simulation.accuracy(metadata.motion());
			System.out.println(formatDouble(accuracy, 4) + " " + simulation.motion() + " " + simulation.configuration() + (!simulation.details().isEmpty() ? " [" + simulation.details() + "]" : ""));

			if (tick > 10) {
				assertTrue(
					accuracy < DIVERGED_MOTION_DISTANCE,
					resourcePath + " tick " + tick + " diverged while replaying movement; distance was " + accuracy
				);
			}
			metadata.assumeOccurred(simulation);
			finishTick(user, simulator, metadata, hasMovement, hasRotation);
		}
	}

	private static void preparePhysicsTestRuntime(MovementRecording recording) {
		MinecraftVersion.setCurrent(recording.serverVersion());
		DrillResolver.manualInit(DenyShapeResolverPipeline.create());
		Fluids.overrideFluids(recording.fluids());
	}

	private static User createReplayUser(
		MovementRecording recording,
		PlaybackBlockCacheView blockCache, World world,
		AtomicReference<Location> currentLocation
	) {
		Player player = FakePlayerFactory.createPlayer(
			(methodName, _) -> switch (methodName) {
				case "getWorld" -> world;
				case "getLocation" -> currentLocation.get().clone();
				case "getUniqueId" -> EMPTY_ID;
				case "isOnGround" -> false;
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

	private static World createReplayWorld() {
		WorldBorder worldBorder = MockWorldBorder.create();
		return FakeWorldFactory.createWorld(
			(methodName, _) -> switch (methodName) {
				case "isChunkLoaded", "isChunkInUse" -> true;
				case "isThundering", "hasStorm" -> false;
				case "getWorldBorder" -> worldBorder;
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
		metadata.refreshFriction(false);
	}

	private static void finishTick(
		User user,
		Simulator simulator,
		MovementMetadata metadata,
		boolean hasMovement,
		boolean hasRotation
	) {
		if (hasMovement) {
			Motion receivedMotion = metadata.motion();
			simulator.simulateAfterTick(user, metadata, metadata.position(), receivedMotion);
			metadata.setBaseMotion(receivedMotion);
			metadata.inactiveTick(
				FLYING_PACKET_ACCURATE,
				FLYING_PACKET_CLIENT,
				NEARBY_COLLISION_INACCURACY,
				ENTITY_USE
			);
		}

		metadata.tick(ELYTRA_FLYING, metadata.elytraFlying);
		metadata.tick(IN_WEB, metadata.inWeb());
		metadata.tick(SNEAKING, metadata.isSneaking());
		metadata.tick(SPRINTING, metadata.isSprinting());
		metadata.tick(IN_WATER, metadata.inWater());
		metadata.inactiveTick(
			BLOCK_PLACEMENT,
			VELOCITY,
			RECEIVED_VELOCITY_PACKET,
			STEP,
			EDGE_SNEAKING,
			SPRINT_CHANGE,
			LONG_TELEPORT,
			FIREWORK_ROCKETS,
			VEHICLE_ATTACHMENT,
			VEHICLE_DETACHMENT
		);
		if (hasMovement || hasRotation) {
			metadata.inactiveTick(EXTERNAL_VELOCITY);
		}

		metadata.reduceTicks = 0;
		metadata.ignoredAttackReduce = false;
		metadata.physicsUnpredictableVelocityExpected = false;
		metadata.step = false;
		metadata.lastKeyStrafe = metadata.keyStrafe;
		metadata.lastKeyForward = metadata.keyForward;
		metadata.lastSprinting = metadata.sprinting;
		metadata.lastSneaking = metadata.sneaking;
		metadata.externalKeyApply = false;
		metadata.lastOnGround = metadata.onGround;
		metadata.setVerifiedLastPosition(metadata.position(), "recording replay");
	}

	private static void applyActionsForTick(
		List<Action> actions,
		MovementMetadata metadata,
		int tick
	) {
		for (Action action : actions) {
			if (action instanceof ReceiveVelocity velocity) {
				if (velocity.tickRange().start() == tick) {
					Motion motion = velocity.motion();
					metadata.baseMotionXBeforeVelocity = metadata.baseMotionX;
					metadata.baseMotionYBeforeVelocity = metadata.baseMotionY;
					metadata.baseMotionZBeforeVelocity = metadata.baseMotionZ;
					metadata.setBaseMotion(motion);
					metadata.lastVelocity = motion.copy();
					metadata.activeTick(EXTERNAL_VELOCITY);
					metadata.activeTick(RECEIVED_VELOCITY_PACKET);
					metadata.activeTick(VELOCITY);
				}
			}
		}
	}

	private static void applyInputsForTick(
		User user, Input input
	) {
		boolean inputIsNotPartial = !user.meta().protocol().sendsInputs() && !MinecraftVersions.VER1_21_3.atOrAbove();
		MovementMetadata movement = user.meta().movement();
		if (inputIsNotPartial) {
			movement.input = input;
		}
		if (movement.sprinting != input.sprinting()) {
			movement.activeTick(SPRINT_CHANGE);
		}
		movement.sneaking = input.sneaking();
		movement.sprinting = input.sprinting();
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

	private static List<Path> findMovementRecordings() throws IOException {
		Path recordingRoot = Paths.get("src", "test", "resources", "physics_test_runs");
		try (Stream<Path> paths = Files.walk(recordingRoot)) {
			return paths
				.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".ptr"))
				.sorted()
				.collect(Collectors.toList());
		}
	}

	private static String resourcePathOf(Path recordingPath) {
		Path resourcesRoot = Paths.get("src", "test", "resources");
		return resourcesRoot.relativize(recordingPath).toString().replace('\\', '/');
	}
}
