package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.block.fluid.FluidFlow;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.shape.resolve.DrillResolver;
import de.jpx3.intave.block.shape.resolve.MockShapeResolverPipeline;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.Collider;
import de.jpx3.intave.player.collider.simple.SimpleCollider;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ExamplePhysicsTest {
	private static final UUID EMPTY_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

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

		int protocolVersion = 47;
		MockFullBlockStaticPlane plane = MockFullBlockStaticPlane.createWithHorizontalPlaneAt(0);

		testUser = UserFactory.createTestUserFor(player, (usr, s) -> switch (s) {
			case "collider" -> collider;
			case "waterflow" -> waterflow;
			case "simplifiedCollider" -> simpleCollider;
			case "blockCache" -> plane;
			case "protocolVersion" -> protocolVersion;
			default -> null;
		});
		UserRepository.manuallyRegisterUser(player, testUser);
	}

	@Test
	public void playerDoesNotFallThroughPlatform() {
		Simulator simulator = Simulators.PLAYER;
		MovementMetadata metadata = testUser.meta().movement();
		metadata.sneaking = true;
		MovementConfiguration configW = MovementConfiguration.blank().pressingW();
		MovementConfiguration configS = MovementConfiguration.blank().pressingS();

		for (int i = 0; i < 500; i++) {
			simulator.simulateBetween(testUser, metadata, i % 2 == 1 ? configW : configS);
//			System.out.println(metadata.position() + " " + metadata.mutableBaseMotionCopy());
			assertTrue(
				metadata.verifiedLastPositionY() >= 1.0,
				"Player fell through the platform at tick " + i + ": " + metadata.verifiedLastPosition()
			);
			assertTrue(
				Math.abs(metadata.verifiedLastPositionX()) <= 100 && Math.abs(metadata.verifiedLastPositionZ()) <= 100,
				"Player flew away at tick " + i + ": " + metadata.verifiedLastPosition()
			);
		}
		assertEquals(1.0, metadata.verifiedLastPositionY(), 1.0E-9);
	}
}
