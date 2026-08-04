package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.block.fluid.FluidFlow;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.shape.resolve.DrillResolver;
import de.jpx3.intave.block.shape.resolve.MockShapeResolverPipeline;
import de.jpx3.intave.check.movement.physics.environment.TestSimulationEnvironment;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.Collider;
import de.jpx3.intave.player.collider.simple.SimpleCollider;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.border.MockWorldBorder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The client counts a player in the swimming pose out of water (crawling under a
 * trapdoor, slab, one-block gap, ...) as {@code isMovingSlowly()} and scales its
 * movement input by the sneaking-speed attribute, exactly like crouching. Missing
 * that made the simulation predict ~3x the acceleration the client applied for the
 * whole time a player was prone.
 */
public final class CrawlingSlowdownTest {
	private static final UUID EMPTY_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
	private static final float MOVE_SPEED = 0.1F;
	private static final double SNEAKING_SPEED = 0.3;

	private User testUser;
	private final Collider collider = Colliders.anyCollider();
	private final FluidFlow waterflow = Fluids.anyWaterflow();
	private final SimpleCollider simpleCollider = Colliders.anySimpleCollider();

	@BeforeEach
	void setUp() {
		MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
		com.comphenix.protocol.utility.MinecraftVersion.setCurrentVersion(com.comphenix.protocol.utility.MinecraftVersion.v1_21_4);

		DrillResolver.manualInit(MockShapeResolverPipeline.createStoneDefault());
		WorldBorder worldBorder = MockWorldBorder.create();
		World world = FakeWorldFactory.createWorld(
			(methodName, _) -> switch (methodName) {
				case "isChunkLoaded", "isChunkInUse" -> true;
				case "isThundering", "hasStorm" -> false;
				case "getWorldBorder" -> worldBorder;
				default -> null;
			}
		);

		Location location = new Location(world, 0, 50, 0);
		Player player = FakePlayerFactory.createPlayer(
			(methodName, _) -> switch (methodName) {
				case "getWorld" -> world;
				case "getLocation" -> location;
				case "getUniqueId" -> EMPTY_ID;
				default -> null;
			}
		);

		MockFullBlockStaticPlane plane = MockFullBlockStaticPlane.createWithHorizontalPlaneAt(0);
		testUser = UserFactory.createTestUserFor(player, (usr, s) -> switch (s) {
			case "collider" -> collider;
			case "waterflow" -> waterflow;
			case "simplifiedCollider" -> simpleCollider;
			case "blockCache" -> plane;
			// 1.21: past the bee update, so the sneaking modifier is driven by the
			// pose alone and not by the raw sneak flag
			case "protocolVersion" -> 767;
			default -> null;
		});
		UserRepository.manuallyRegisterUser(player, testUser);
	}

	@Test
	public void crawlingScalesMovementInputLikeTheClient() {
		double standing = forwardAccelerationWith(Pose.STANDING, false);
		double crawling = forwardAccelerationWith(Pose.SWIMMING, false);

		assertEquals(MOVE_SPEED * 0.98, standing, 1.0E-6, "standing acceleration");
		assertEquals(standing * SNEAKING_SPEED, crawling, 1.0E-6, "crawling acceleration");
	}

	@Test
	public void swimmingInWaterIsNotSlowedDown() {
		// isVisuallyCrawling() is the swimming pose *out of water*; a player actually
		// swimming keeps the full input. (The absolute value differs from the ground
		// case because the water path derives its own move speed.)
		double inWaterStanding = forwardAccelerationWith(Pose.STANDING, true);
		double inWaterSwimming = forwardAccelerationWith(Pose.SWIMMING, true);

		assertEquals(inWaterStanding, inWaterSwimming, 1.0E-6, "swimming acceleration");
	}

	private double forwardAccelerationWith(Pose pose, boolean inWater) {
		TestSimulationEnvironment environment = new TestSimulationEnvironment();
		environment.setPose(pose);
		environment.setInWater(inWater);
		environment.setPositionY(50);
		environment.setVerifiedPositionY(50);
		environment.setLastPositionY(50);
		// friction() is the friction-influenced move speed handed to moveRelative; in
		// water the simulator derives its own from the depth strider modifier
		environment.setFriction(MOVE_SPEED);
		environment.setAiMovementSpeed(MOVE_SPEED);

		Simulation simulation = Simulators.PLAYER.simulateTick(
			testUser, Motion.newEmpty(), environment,
			MovementConfiguration.blank().pressingW()
		);
		// yaw 0 => forward is +Z
		return simulation.motion().motionZ;
	}
}
