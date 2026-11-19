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
import de.jpx3.intave.block.physics.BlockPhysics;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.MovementCharacteristics;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.stream.Stream;

import static de.jpx3.intave.user.meta.ProtocolMetadata.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class BaseSimulator262MovementAttributesTest {
  private static final double EPSILON = 1.0E-12D;
  private static final Position POSITION = Position.of(0.5D, 50.0D, 0.5D);
  private static final Rotation ROTATION = Rotation.zero();
  private static final MovementConfiguration CONFIGURATION = MovementConfiguration.blank();

  @BeforeEach
  void setUp() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_2);
    BlockPhysics.setup(MinecraftVersions.VER26_2);
  }

  @AfterEach
  void tearDown() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    BlockPhysics.setup(MinecraftVersions.VER1_21_4);
  }

  @Test
  void modifiedFrictionUsesTheClientFloatFormulaAndClamp() {
    assertEquals(0.6F, MovementCharacteristics.computeModifiedFriction(0.6F, 1.0F));
    assertEquals(0.8F, MovementCharacteristics.computeModifiedFriction(0.6F, 0.5F));
    assertEquals(
      1.0F - (1.0F - 0.6F) * 2.0F,
      MovementCharacteristics.computeModifiedFriction(0.6F, 2.0F)
    );
    assertEquals(0.0F, MovementCharacteristics.computeModifiedFriction(0.6F, 4.0F));
    assertEquals(1.0F, MovementCharacteristics.computeModifiedFriction(0.6F, 0.0F));
  }

  @Test
  void blockRestitutionMatches262BlockProperties() {
    Material bed = Stream.of(Material.values())
      .filter(material -> material.name().endsWith("_BED"))
      .findFirst()
      .orElseThrow(AssertionError::new);

    assertEquals(1.0F, BlockProperties.of(Material.SLIME_BLOCK).bounceRestitution());
    assertEquals(0.75F, BlockProperties.of(bed).bounceRestitution());
    assertEquals(0.0F, BlockProperties.of(Material.STONE).bounceRestitution());
  }

  @Test
  void frictionModifierChanges262GroundAcceleration() {
    TestContext context = context(
      VER_26_2,
      MockFullBlockStaticPlane.createWithHorizontalPlaneAt(49)
    );
    context.environment.setLastOnGround(true);
    context.environment.setFrictionMaterial(Material.STONE);
    context.user.meta().abilities().modifyBaseValue("friction_modifier", 0.5D);

    float actual = MovementCharacteristics.resolveFriction(
      context.user,
      context.environment,
      false,
      POSITION.getX(), POSITION.getY(), POSITION.getZ()
    );
    float blockFriction = MovementCharacteristics.computeModifiedFriction(0.6F, 0.5F);
    float expected = context.environment.aiMoveSpeed(false)
      * (0.21600002F / (blockFriction * blockFriction * blockFriction));

    assertEquals(expected, actual);
  }

  @Test
  void pre262GroundAccelerationIgnoresFrictionModifier() {
    TestContext context = context(
      VER_26_1_1,
      MockFullBlockStaticPlane.createWithHorizontalPlaneAt(49)
    );
    context.environment.setLastOnGround(true);
    context.environment.setFrictionMaterial(Material.STONE);

    float baseline = MovementCharacteristics.resolveFriction(
      context.user,
      context.environment,
      false,
      POSITION.getX(), POSITION.getY(), POSITION.getZ()
    );
    context.user.meta().abilities().modifyBaseValue("friction_modifier", 0.5D);
    float modified = MovementCharacteristics.resolveFriction(
      context.user,
      context.environment,
      false,
      POSITION.getX(), POSITION.getY(), POSITION.getZ()
    );

    assertEquals(baseline, modified);
  }

  @Test
  void airDragModifierChanges262HorizontalAndVerticalDrag() {
    TestContext context = context(VER_26_2, new MockFullBlockStaticPlane());
    context.user.meta().abilities().modifyBaseValue("air_drag_modifier", 2.0D);
    Motion input = new Motion(0.8D, 0.2D, -0.6D);

    Motion actual = simulateAfterTick(
      context,
      input,
      SimulationResult.untouched(input.copy()),
      false,
      Material.AIR
    );
    float horizontalDrag = MovementCharacteristics.computeModifiedFriction(0.91F, 2.0F);
    float verticalDrag = MovementCharacteristics.computeModifiedFriction(0.98F, 2.0F);

    assertEquals(input.motionX * horizontalDrag, actual.motionX, EPSILON);
    assertEquals((input.motionY - context.environment.gravity()) * verticalDrag, actual.motionY, EPSILON);
    assertEquals(input.motionZ * horizontalDrag, actual.motionZ, EPSILON);
  }

  @Test
  void pre262AirMovementIgnoresAirDragModifier() {
    TestContext context = context(VER_26_1_1, new MockFullBlockStaticPlane());
    context.user.meta().abilities().modifyBaseValue("air_drag_modifier", 2.0D);
    Motion input = new Motion(0.8D, 0.2D, -0.6D);

    Motion actual = simulateAfterTick(
      context,
      input,
      SimulationResult.untouched(input.copy()),
      false,
      Material.AIR
    );

    assertEquals(input.motionX * 0.91F, actual.motionX, EPSILON);
    assertEquals((input.motionY - context.environment.gravity()) * 0.98F, actual.motionY, EPSILON);
    assertEquals(input.motionZ * 0.91F, actual.motionZ, EPSILON);
  }

  @Test
  void frictionModifierAlsoChanges262GroundDrag() {
    TestContext context = context(
      VER_26_2,
      MockFullBlockStaticPlane.createWithHorizontalPlaneAt(49)
    );
    context.user.meta().abilities().modifyBaseValue("friction_modifier", 0.0D);
    Motion input = new Motion(0.8D, 0.2D, -0.6D);

    Motion actual = simulateAfterTick(
      context,
      input,
      SimulationResult.untouched(input.copy()),
      true,
      Material.STONE
    );
    float expectedDrag = MovementCharacteristics.computeModifiedFriction(0.6F, 0.0F) * 0.91F;

    assertEquals(input.motionX * expectedDrag, actual.motionX, EPSILON);
    assertEquals(input.motionZ * expectedDrag, actual.motionZ, EPSILON);
  }

  @Test
  void bouncinessReflectsHorizontal262Collisions() {
    TestContext context = context(VER_26_2, new MockFullBlockStaticPlane());
    context.user.meta().abilities().modifyBaseValue("bounciness", 0.5D);
    Motion input = new Motion(0.4D, 0.0D, -0.3D);
    Motion clipped = new Motion(0.0D, 0.0D, -0.3D);
    SimulationResult collision = collisionResult(input, clipped, false, true, false, true, false);

    Motion actual = simulateAfterTick(context, clipped, collision, false, Material.AIR);

    assertEquals(-input.motionX * 0.5D * 0.91F, actual.motionX, EPSILON);
    assertEquals(input.motionZ * 0.91F, actual.motionZ, EPSILON);
  }

  @Test
  void pre262HorizontalCollisionsStillResetMotion() {
    TestContext context = context(VER_26_1_1, new MockFullBlockStaticPlane());
    context.user.meta().abilities().modifyBaseValue("bounciness", 0.5D);
    Motion input = new Motion(0.4D, 0.0D, -0.3D);
    Motion clipped = new Motion(0.0D, 0.0D, -0.3D);
    SimulationResult collision = collisionResult(input, clipped, false, true, false, true, false);

    Motion actual = simulateAfterTick(context, input, collision, false, Material.AIR);

    assertEquals(0.0D, actual.motionX, EPSILON);
    assertEquals(input.motionZ * 0.91F, actual.motionZ, EPSILON);
  }

  @Test
  void sneakingSuppresses262Bounciness() {
    TestContext context = context(VER_26_2, new MockFullBlockStaticPlane());
    context.user.meta().abilities().modifyBaseValue("bounciness", 1.0D);
    context.environment.sneaking = true;
    Motion input = new Motion(0.4D, 0.0D, -0.3D);
    Motion clipped = new Motion(0.0D, 0.0D, -0.3D);
    SimulationResult collision = collisionResult(input, clipped, false, true, false, true, false);

    Motion actual = simulateAfterTick(context, input, collision, false, Material.AIR);

    assertEquals(0.0D, actual.motionX, EPSILON);
  }

  @Test
  void bouncinessRestitutesVertical262CollisionsBeforeGravityAndDrag() {
    TestContext context = context(
      VER_26_2,
      MockFullBlockStaticPlane.createWithHorizontalPlaneAt(49)
    );
    context.user.meta().abilities().modifyBaseValue("bounciness", 0.5D);
    Motion input = new Motion(0.0D, -0.5D, 0.0D);
    Motion clipped = Motion.newEmpty();
    SimulationResult collision = collisionResult(input, clipped, true, false, true, false, false);

    Motion actual = simulateAfterTick(context, clipped, collision, false, Material.STONE);
    double restitutionMotion = -input.motionY * 0.5D;
    double expected = (restitutionMotion - context.environment.gravity()) * 0.98F;

    assertEquals(expected, actual.motionY, EPSILON);
  }

  @Test
  void bouncingAtExactlyGravityStopsIn263While262KeepsItsBoundary() {
    for (int protocol : new int[]{VER_26_2, VER_26_3}) {
      for (double speed : new double[]{Math.nextDown(0.08D), 0.08D, Math.nextUp(0.08D)}) {
        TestContext context = context(protocol, materialPlane(Material.SLIME_BLOCK));
        double gravity = context.environment.gravity();
        assertEquals(0.08D, gravity);
        Motion input = new Motion(0.0D, -speed, 0.0D);
        Motion clipped = Motion.newEmpty();
        SimulationResult collision = collisionResult(input, clipped, true, false, true, false, false);
        Motion actual = simulateAfterTick(context, clipped, collision, false, Material.SLIME_BLOCK);
        boolean bounces = protocol == VER_26_2 ? speed >= gravity : speed > gravity;
        double expected = ((bounces ? speed : 0.0D) - gravity) * 0.98F;
        assertEquals(expected, actual.motionY, "protocol=" + protocol + ", speed=" + speed);
      }
    }
  }

  @Test
  void slimeCannotRestoreVelocityClearedByAWebBeforeCollision() {
    for (String server : new String[]{"1.8.8", "1.21.4", "26.2"}) {
      MinecraftVersion.setCurrent(new MinecraftVersion(server));
      for (int protocol : new int[]{VER_26_2, VER_26_3}) {
        for (boolean multiplier : new boolean[]{false, true}) {
          TestContext context = context(protocol, materialPlane(Material.SLIME_BLOCK));
          context.environment.setInWeb(!multiplier);
          if (multiplier) {
            context.environment.setMotionMultiplier(new org.bukkit.util.Vector(0.25D, 0.05F, 0.25D));
          }
          Motion slowedDisplacement = new Motion(0.0D, -0.1D, 0.0D);
          Motion storedVelocity = multiplier ? slowedDisplacement.copy() : Motion.newEmpty();
          SimulationResult collision = new SimulationResult(
            storedVelocity, Motion.newEmpty(), slowedDisplacement,
            true, false, true, false, false, false, false, 0.0D
          );
          Motion actual = simulateAfterTick(context, storedVelocity, collision, false, Material.SLIME_BLOCK);
          // Entity.move clears stored velocity before colliding the slowed displacement.
          // Restitution sees zero, then travel applies gravity and vertical drag.
          assertEquals(-0.08D * 0.98F, actual.motionY, EPSILON,
            "server=" + server + ", protocol=" + protocol + ", multiplier=" + multiplier);
        }
      }
    }
  }

  @Test
  void shelfMushroomsBounceAndStrawBedsDoNot() {
    assertEquals(0.75F, BlockProperties.of(Material.valueOf("SHELF_MUSHROOM")).bounceRestitution());
    assertEquals(0.0F, BlockProperties.of(Material.valueOf("STRAW_BED")).bounceRestitution());
    TestContext context = context(VER_26_3, materialPlane(Material.valueOf("SHELF_MUSHROOM")));
    Motion input = new Motion(0.0D, -0.5D, 0.0D);
    Motion clipped = Motion.newEmpty();
    SimulationResult collision = collisionResult(input, clipped, true, false, true, false, false);
    Motion actual = simulateAfterTick(context, clipped, collision, false, Material.valueOf("SHELF_MUSHROOM"));
    assertEquals((0.5D * 0.75F - context.environment.gravity()) * 0.98F, actual.motionY, EPSILON);
  }

  private static BlockCache materialPlane(Material material) {
    BlockCache plane = MockFullBlockStaticPlane.createWithHorizontalPlaneAt(49);
    return (BlockCache) Proxy.newProxyInstance(BlockCache.class.getClassLoader(), new Class<?>[]{BlockCache.class}, (proxy, method, args) -> {
      Object value = method.invoke(plane, args);
      if (value == Material.STONE) return material;
      if (value instanceof BlockCacheEntry state && state.type() == Material.STONE) {
        return new BlockCacheEntry(state.outlineShape(), state.collisionShape(), material, state.variantIndex());
      }
      return value;
    });
  }

  private static Motion simulateAfterTick(
    TestContext context,
    Motion input,
    SimulationResult result,
    boolean lastOnGround,
    Material frictionMaterial
  ) {
    MovementMetadata environment = context.environment;
    environment.setLastOnGround(lastOnGround);
    environment.onGround = result.onGround();
    environment.collidedHorizontally = result.collidedHorizontally();
    environment.collidedVertically = result.collidedVertically();
    environment.physicsResetMotionX = result.resetMotionX();
    environment.physicsResetMotionZ = result.resetMotionZ();
    environment.setSimulationResult(result);
    environment.setBaseMotion(input);
    environment.setFrictionMaterial(frictionMaterial);
    environment.setCollideMaterial(frictionMaterial);
    environment.setInWater(false);
    environment.setInLava(false);

    return Simulators.PLAYER.simulateAfterTick(
      context.user,
      environment.mutableView(),
      CONFIGURATION,
      POSITION,
      input
    );
  }

  private static SimulationResult collisionResult(
    Motion input,
    Motion clipped,
    boolean onGround,
    boolean collidedHorizontally,
    boolean collidedVertically,
    boolean resetMotionX,
    boolean resetMotionZ
  ) {
    return new SimulationResult(
      input.copy(),
      clipped.copy(),
      input.copy(),
      onGround,
      collidedHorizontally,
      collidedVertically,
      resetMotionX,
      resetMotionZ,
      false,
      false,
      0.0D
    );
  }

  private static TestContext context(int protocolVersion, BlockCache blockCache) {
    World world = FakeWorldFactory.createWorld((methodName, ignored) -> switch (methodName) {
      case "isChunkLoaded", "isChunkInUse" -> true;
      case "isThundering", "hasStorm" -> false;
      default -> null;
    });
    Location location = POSITION.toLocation(world);
    UUID playerId = UUID.randomUUID();
    Player player = FakePlayerFactory.createPlayer((methodName, ignored) -> switch (methodName) {
      case "getWorld" -> world;
      case "getLocation" -> location;
      case "getUniqueId" -> playerId;
      default -> null;
    });
    User user = UserFactory.createTestUserFor(player, (ignored, key) -> switch (key) {
      case "blockCache" -> blockCache;
      case "protocolVersion" -> protocolVersion;
      default -> null;
    });
    UserRepository.manuallyRegisterUser(player, user);

    MovementMetadata environment = user.meta().movement();
    environment.updateMovement(POSITION, ROTATION);
    environment.setVerifiedLastPosition(POSITION, "26.2 movement attribute test seed");
    environment.setLastPosition(POSITION);
    return new TestContext(user, environment);
  }

  private record TestContext(User user, MovementMetadata environment) {
  }
}
