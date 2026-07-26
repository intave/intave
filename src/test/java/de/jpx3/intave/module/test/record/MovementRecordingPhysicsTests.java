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

package de.jpx3.intave.module.test.record;

import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.PlaybackBlockCacheView;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.physics.BlockPhysics;
import de.jpx3.intave.block.shape.resolve.DenyShapeResolverPipeline;
import de.jpx3.intave.block.shape.resolve.DrillResolver;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.search.SimulationSearch;
import de.jpx3.intave.check.movement.physics.search.ThreeTickSimulationSearch;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
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
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.*;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

final class MovementRecordingPhysicsTests {
	private static final UUID EMPTY_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
	private static final double DIVERGED_MOTION_DISTANCE = 0.01;

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

	static void processRecordingResource(String resourcePath) throws IOException {
		MovementRecording recording = MovementRecording.loadFrom(
			Resources.resourceFromJarOrTestBuild(resourcePath)
		);
		preparePhysicsTestRuntime(recording);
		processRecording(resourcePath, recording);
	}

	static void processRecording(
		String resourcePath,
		MovementRecording recording
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
		Position initialPosition = Objects.requireNonNull(firstFrame.moveTo(), "initial position cannot be null");
		Rotation initialRotation = firstFrame.rotateTo() == null ? Rotation.zero() : firstFrame.rotateTo();
		AtomicReference<Location> currentLocation = new AtomicReference<>();
		World world = createReplayWorld();
		currentLocation.set(locationOf(world, initialPosition, initialRotation));

		User user = createReplayUser(recording, blockCache, world, currentLocation);
		MovementMetadata metadata = user.meta().movement();
		applyAttributesForTick(recording, user, firstPositionFrame);
		seedInitialMovementState(user, metadata, initialPosition, initialRotation);

		SimulationSearch processor = new ThreeTickSimulationSearch(false, false);
		Simulator simulator = Simulators.PLAYER;
		List<String> lastMessages = new LinkedList<>();

		for (int tick = firstPositionFrame + 1; tick < frames.size(); tick++) {
			MoveFrame frame = frames.get(tick);
			Input input = frame.input();

			applyAttributesForTick(recording, user, tick);
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
			metadata.treatThisFlyPacketAsMovePacket = false;

			if (!hasMovement) {
				continue;
			}

			Motion previousBaseMotion = metadata.mutableBaseMotionCopy();
			Motion preTickMotion = simulator.simulatePreTick(user, previousBaseMotion.copy(), metadata);
			metadata.setBaseMotion(preTickMotion);

			Simulation simulation = processor.greedyFullTickSearch(user, metadata.mutableView(), simulator);
//			Simulation simulation = processor.simulate(user, simulator, hasMovement || hasRotation);
//			boolean subversiveFlyingMovement = subversiveFlyingMovement(user, simulationEnvironment, simulation, hasMovement);
//			if (!hasMovement && !hasRotation && !subversiveFlyingMovement) {
//				metadata.setBaseMotion(previousBaseMotion);
//				finishTick(user, simulator, metadata, false, false);
//				continue;
//			}

			double loss = simulation.positionDifference(metadata.position());
			double allowedLoss = DIVERGED_MOTION_DISTANCE;
			String output = formatDouble(loss, 4) + " " + simulation.offsetMotion() + " [actual: " + metadata.sentOffsetMotion() + "] " + simulation.configuration() + (!simulation.blueDetails().isEmpty() ? " [" + simulation.blueDetails() + "]" : "");
			lastMessages.add(output);

//			System.out.println(loss);

			if (loss > allowedLoss && tick > 16) {
				System.out.println("\r" + "[FAILED] " + resourcePath + " (tick " + tick + ")");
				System.err.println("==== <HEAD> ====");
				System.err.println("Physics test " + resourcePath + " has failed");
				System.err.println("Tick " + tick + " is incorrect");
				System.err.println("Loss: " + loss);
				System.err.println("Allowed loss: " + allowedLoss);
				System.err.println("==== </HEAD> ====");

				System.err.println("==== <HISTORY> ====");
				for (String lastMessage : lastMessages) {
					System.err.println(lastMessage);
				}
				System.err.println("==== </HISTORY> ====");

				System.err.println("==== <USERDATA> ====");
				System.err.println("Input: " + metadata.input);
				System.err.println("Position");
				System.err.println("  Sim   " + metadata.lastPosition().mutable().add(simulation.offsetMotion()));
				System.err.println("  Sent  " + metadata.position());
				System.err.println("  Last  " + metadata.lastPosition());
				System.err.println("  LastV " + metadata.verifiedLastPosition());
				Position nextPosition;
				if (frames.size() > tick + 1 && (nextPosition = frames.get(tick + 1).moveTo()) != null) {
					System.err.println("  Next " + nextPosition + " (dy to sent: " + (nextPosition.getY() - metadata.position().getY()) + ")");
				}
				System.err.println("Motion");
				System.err.println("  Sim   " + simulation.offsetMotion());
				System.err.println("  Sent  " + metadata.sentOffsetMotion());
				System.err.println("  Base  " + metadata.mutableBaseMotionCopy());
				System.err.println("Rotation: " + metadata.rotation());
				System.err.println("Motion: " + metadata.sentOffsetMotion());
				System.err.println("Ground");
				System.err.println("  Current " + metadata.onGround());
				System.err.println("  Last " + metadata.lastOnGround());
				System.err.println("Sneaking " + metadata.isSneaking());
				System.err.println("==== </USERDATA> ====");
				System.err.println("Movement diverged at tick " + tick + " with a distance of " + loss);
				fail();
			}

			System.out.print("\r" + output);

//			if (subversiveFlyingMovement) {
//				simulationEnvironment.reinterpretMovePacket(simulation);
//			}
			simulation.environment().commitTo(metadata);
			metadata.assumeOccurred(simulation);
			finishTick(user, simulator, metadata, hasMovement, hasRotation);

			if (lastMessages.size() > 16) {
				lastMessages.removeFirst();
			}
		}

		System.out.println("\r[SUCCESS] " + resourcePath + "...");
	}

	private static boolean subversiveFlyingMovement(
		User user,
		SimulationEnvironment environment,
		Simulation simulation,
		boolean hasMovement
	) {
		return !hasMovement && !simulation.offsetMotion().isZero() && simulation.resultsInFlyingPacket(
			environment,
			user.meta().protocol().flyingPacketUncertaintyRadius()
		);
	}

	private static void preparePhysicsTestRuntime(MovementRecording recording) {
		MinecraftVersion serverVersion = recording.serverVersion();
		MinecraftVersion.setCurrent(serverVersion);
		DrillResolver.manualInit(DenyShapeResolverPipeline.create());
		Fluids.overrideFluids(recording.fluids());
		BlockPhysics.setup(serverVersion);
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
		Simulator simulator,
		MovementMetadata metadata,
		boolean hasMovement,
		boolean hasRotation
	) {
		if (hasMovement) {
			Motion afterTickMotion = simulator.simulateAfterTick(
				user, metadata, MovementConfiguration.blank(),
				metadata.position(),
				metadata.sentOffsetMotion()
			);
			metadata.setBaseMotion(afterTickMotion);
			metadata.inactiveTick(
				FLYING_PACKET_ACCURATE,
				FLYING_PACKET_CLIENT,
				NEARBY_COLLISION_INACCURACY,
				ENTITY_USE
			);
		} else if (hasRotation || metadata.treatThisFlyPacketAsMovePacket) {
			Motion afterTickMotion = simulator.simulateAfterTick(
				user,
				metadata,
				MovementConfiguration.blank(),
				metadata.lastPosition().mutable().add(metadata.sentOffsetMotion()),
				metadata.sentOffsetMotion()
			);
			metadata.setBaseMotion(afterTickMotion);
		}

		metadata.tickComplete(hasMovement, hasRotation, true);

		metadata.lastKeyStrafe = metadata.keyStrafe;
		metadata.lastKeyForward = metadata.keyForward;
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
		MovementMetadata movement = user.meta().movement();
		if (user.meta().protocol().sendsInputs() && MinecraftVersions.VER1_21_3.atOrAbove()) {
			movement.input = input;
		}
		boolean sprinting = user.meta().protocol().sendsInputs()
			? input.sprintKey() || movement.sprinting && input.forwardKey()
			: input.sprintKey();
		if (movement.sprinting != sprinting) {
			movement.activeTick(SPRINT_CHANGE);
		}
		movement.sneaking = input.sneakKey();
		movement.sprinting = sprinting;
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
				&& abilities.modifiersOf(movementSpeed).stream()
				.anyMatch(modifier -> !AbilityMetadata.EXCLUDE_SPRINT_MODIFIER.test(modifier));
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
