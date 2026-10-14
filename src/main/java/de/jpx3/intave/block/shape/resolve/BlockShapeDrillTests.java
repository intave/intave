package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.BlockAccess;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.cache.BlockCaches;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.test.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.version.ServerProtocolVersion;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.share.Direction.Axis.Y_AXIS;

public final class BlockShapeDrillTests extends IntegrationTests {
  private Block block;
  private Player player;
  private User user;
  private BlockStorage priorMaterial;
  private ShapeResolverPipeline drill;

  public BlockShapeDrillTests() {
    super("BSD");
  }

  @Before
  public void setup() {
    World world = Bukkit.getWorlds().get(0);
    block = world.getBlockAt(0, 0, 0);
    priorMaterial = BlockStorage.store(block);
    player = FakePlayerFactory.createPlayer(
      (methodName, args) -> {
        switch (methodName) {
          case "getWorld":
            return world;
          case "getInventory":
            return new MockEmptyInventory();
          case "getLocation":
            return new Location(world, 0, 0, 0);
          case "getUniqueId":
            return UUID.randomUUID();
          case "getActivePotionEffects":
            return Collections.emptyList();
        }
        return null;
      }
    );
    user = UserFactory.createTestUserFor(player);
    UserRepository.manuallyRegisterUser(player, user);
    drill = DrillResolver.selectedDrill();
    block.setType(Material.AIR);
  }

  @Test(
    testCode = "A",
    severity = Severity.ERROR
  )
  public void testSolidCollision() {
    block.setType(Material.DIAMOND_BLOCK);
    BlockShape blockShape = drill.collisionShapeOf(block.getWorld(), user, block.getType(), 0, 0, 0, 0);
    assertTrue(blockShape.isCubic());
  }

  @Test(
    testCode = "B",
    severity = Severity.ERROR
  )
  public void testSolidOutline() {
    block.setType(Material.DIAMOND_BLOCK);
    BlockShape blockShape = drill.outlineShapeOf(block.getWorld(), user, block.getType(), 0, 0, 0, 0);
    assertTrue(blockShape.isCubic());
  }

  @Test(
    testCode = "C",
    severity = Severity.ERROR
  )
  public void testTransparentCollision() {
    block.setType(Material.AIR, false);
    BlockShape blockShape = drill.collisionShapeOf(block.getWorld(), user, block.getType(), 0, 0, 0, 0);
    assertTrue(blockShape.isEmpty());
  }

  // todo fixme
//  @Test(
//    testCode = "D",
//    severity = Severity.WARNING
//  )
//  public void testTransparentOutline() {
//    block.setType(Material.AIR, false);
//    Material type = block.getType();
//    BlockShape blockShape = drill.outlineShapeOf(block.getWorld(), user, type, 0, 0, 0, 0);
//    if (!blockShape.isEmpty() && IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
//      System.out.println(type);
//      System.out.println(blockShape);
//    }
//    assertTrue(blockShape.isEmpty());
//  }

  @Test(
    testCode = "E",
    severity = Severity.ERROR
  )
  public void testDeviationFromActualCollision() {
    block.setType(Material.AIR, false);
    BlockShape blockShape = drill.collisionShapeOf(block.getWorld(), user, Material.DIAMOND_BLOCK, 0, 0, 0, 0);
    assertTrue(blockShape.isCubic());
  }

  @Test(
    testCode = "F",
    severity = Severity.WARNING
  )
  public void testDeviationFromActualOutline() {
    block.setType(Material.AIR, false);
    BlockShape blockShape = drill.outlineShapeOf(block.getWorld(), user, Material.DIAMOND_BLOCK, 0, 0, 0, 0);
    assertTrue(blockShape.isCubic());
  }

  @Test(
    testCode = "G",
    severity = Severity.ERROR
  )
  public void testComplexCollisionShape() {
    block.setType(Material.ANVIL, false);
    Material type = block.getType();
    BlockShape blockShape = drill.collisionShapeOf(block.getWorld(), user, type, 0, 0, 0, 0);
    assertFalse(blockShape.isCubic());
  }

  @Test(testCode = "IRON-BARS-26.3", severity = Severity.ERROR)
  public void testViaVersionIronBarsUseFullCrossBesideLeaves() {
    if (MinecraftVersions.VER1_8_0.below() || MinecraftVersions.VER1_9_0.atOrAbove()) {
      return;
    }

    Material ironBars = Material.getMaterial("IRON_FENCE");
    Material leaves = Material.getMaterial("LEAVES");
    assertNotNull(ironBars);
    assertNotNull(leaves);

    World world = block.getWorld();
    int x = 8;
    int y = 200;
    int z = 8;
    List<org.bukkit.block.BlockState> original = new ArrayList<>();
    for (int[] offset : new int[][] {{0, 0}, {0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
      original.add(world.getBlockAt(x + offset[0], y, z + offset[1]).getState());
    }

    try {
      world.getBlockAt(x, y, z).setType(ironBars, false);
      world.getBlockAt(x, y, z - 1).setType(Material.AIR, false);
      world.getBlockAt(x + 1, y, z).setType(leaves, false);
      world.getBlockAt(x, y, z + 1).setType(Material.AIR, false);
      world.getBlockAt(x - 1, y, z).setType(Material.AIR, false);

      ShapeResolverPipeline pipeline = ShapeResolver.createPipelineFor(drill);
      BlockCache cache = BlockCaches.cacheForPlayerWithResolver(player, pipeline);
      User modernUser = UserFactory.createTestUserFor(player, (usr, key) -> {
        if (key.equals("protocolVersion")) {
          return de.jpx3.intave.user.meta.ProtocolMetadata.VER_26_3;
        }
        if (key.equals("blockCache")) {
          return cache;
        }
        return null;
      });
      UserRepository.manuallyRegisterUser(player, modernUser);

      BlockShape shape = cache.collisionShapeAt(x, y, z);
      assertTrue(shape.strictlyInside(x + 0.5, y + 0.5, z + 0.25));
      assertTrue(shape.strictlyInside(x + 0.75, y + 0.5, z + 0.5));
      assertTrue(shape.strictlyInside(x + 0.5, y + 0.5, z + 0.75));
      assertTrue(shape.strictlyInside(x + 0.25, y + 0.5, z + 0.5));
    } finally {
      for (org.bukkit.block.BlockState state : original) {
        state.update(true, false);
      }
    }
  }

  @Test(testCode = "GRASS", severity = Severity.ERROR)
  public void testGrassCollisionShapes() {
    if (!MinecraftVersions.VER26_2.atOrAbove()) {
      return;
    }
    // Exercise both the native shape reader and its material/variant caches.
    ShapeResolverPipeline pipeline = ShapeResolver.createPipelineFor(drill);
    for (String name : new String[]{"GRASS_BLOCK", "SHORT_GRASS", "TALL_GRASS"}) {
      Material material = Material.getMaterial(name);
      assertNotNull(material);
      assertFalse(BlockVariantRegister.variantIdsOf(material).isEmpty());
      boolean solid = name.equals("GRASS_BLOCK");
      for (int variant : BlockVariantRegister.variantIdsOf(material)) {
        for (ShapeResolverPipeline resolver : new ShapeResolverPipeline[]{drill, pipeline}) {
          for (int y : new int[]{-32, 0, 64}) {
            BlockShape shape = resolver.collisionShapeOf(block.getWorld(), user, material, variant, 0, y, 0);
            if (solid) {
              assertEquals(List.of(new BoundingBox(0, y, 0, 1, y + 1, 1)), shape.elementaryBoxes());
              BoundingBox standing = new BoundingBox(0.2, y + 1, 0.2, 0.8, y + 2.8, 0.8);
              assertEquals(0.0, shape.allowedOffset(Y_AXIS, standing, -0.08));
            } else if (!shape.isEmpty()) {
              fail(name + " variant " + variant + " has a collision shape: " + shape.elementaryBoxes());
            }
          }
        }
      }
    }
  }

  @Test(testCode = "GRASS-MOVE", severity = Severity.ERROR)
  public void testMovementThroughGrassAndOntoGrassBlocks() throws Exception {
    if (!MinecraftVersions.VER26_2.atOrAbove()) {
      return;
    }
    World world = block.getWorld();
    // Keep the fixture clear of the generated terrain and restore exact block states.
    List<org.bukkit.block.BlockState> original = new ArrayList<>();
    BlockCache cache = BlockCaches.cacheForPlayer(player);
    User collisionUser = UserFactory.createTestUserFor(player, (usr, key) -> {
      if (key.equals("protocolVersion")) {
        return ServerProtocolVersion.current();
      }
      if (key.equals("blockCache")) {
        return cache;
      }
      return null;
    });
    try {
      for (int x = 0; x <= 2; x++) {
        for (int z = 0; z <= 1; z++) {
          for (int y = 200; y <= 203; y++) {
            Block fixtureBlock = world.getBlockAt(x, y, z);
            original.add(fixtureBlock.getState());
            fixtureBlock.setType(Material.getMaterial(y == 200 ? "GRASS_BLOCK" : "AIR"), false);
          }
        }
      }
      for (String name : new String[]{"SHORT_GRASS", "TALL_GRASS", "GRASS_BLOCK"}) {
        world.getBlockAt(1, 201, 0).setType(Material.getMaterial(name), false);
        cache.invalidateAll();
          MockSimulationEnvironment environment = new MockSimulationEnvironment();
          BoundingBox box = new BoundingBox(0.2, 201, 0.2, 0.8, 202.8, 0.8);
          environment.setBoundingBox(box);
          environment.setPosition(0.5, 201, 0.5);
          environment.setOnGround(true);
          for (double dx : new double[]{0.8, 0.4}) {
            SimulationResult result = CompletableFuture.supplyAsync(() -> collisionUser.collider().collide(
              collisionUser, environment, new Motion(dx, -0.08, 0), 0.5, 201, 0.5, false)).get(5, TimeUnit.SECONDS);
            assertEquals(name.equals("GRASS_BLOCK") ? 1.0 - box.maxX : dx, result.offsetMotion().motionX);
            assertEquals(0.0, result.offsetMotion().motionY);
            assertTrue(result.onGround());
          }
      }
    } finally {
      for (org.bukkit.block.BlockState state : original) {
        state.update(true, false);
      }
    }
  }

  @Test(testCode = "GRASS-ASYNC", severity = Severity.ERROR)
  public void testGrassCollisionOffServerThread() throws Exception {
    if (!MinecraftVersions.VER26_2.atOrAbove()) {
      return;
    }
    block.setType(Material.getMaterial("GRASS_BLOCK"), false);
    World world = block.getWorld();
    Material material = block.getType();
    BlockShape shape = CompletableFuture.supplyAsync(() -> drill.collisionShapeOf(
      world, user, material, 0, 0, 0, 0)).get(5, TimeUnit.SECONDS);
    assertEquals(material, CompletableFuture.supplyAsync(() -> BlockAccess.global().typeOf(block)).get(5, TimeUnit.SECONDS));
    if (!shape.isCubic()) {
      fail("Loaded grass block has no solid collision shape off the server thread: " + shape.elementaryBoxes());
    }
  }

  @After
  public void teardown() {
    priorMaterial.restore();
    UserRepository.unregisterUser(player);
  }
}
