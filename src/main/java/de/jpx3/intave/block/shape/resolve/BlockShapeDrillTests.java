package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.test.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class BlockShapeDrillTests extends Tests {
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
    player = FakePlayerFactory.createPlayer();
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
    BlockShape blockShape = drill.collisionShapeOf(block.getWorld(), player, block.getType(), 0, 0, 0, 0);
    assertTrue(blockShape.isCubic());
  }

  @Test(
    testCode = "B",
    severity = Severity.ERROR
  )
  public void testSolidOutline() {
    block.setType(Material.DIAMOND_BLOCK);
    BlockShape blockShape = drill.outlineShapeOf(block.getWorld(), player, block.getType(), 0, 0, 0, 0);
    assertTrue(blockShape.isCubic());
  }

  @Test(
    testCode = "C",
    severity = Severity.ERROR
  )
  public void testTransparentCollision() {
    block.setType(Material.AIR, false);
    BlockShape blockShape = drill.collisionShapeOf(block.getWorld(), player, block.getType(), 0, 0, 0, 0);
    assertTrue(blockShape.isEmpty());
  }

  // todo fixme
  @Test(
    testCode = "D",
    severity = Severity.WARNING
  )
  public void testTransparentOutline() {
    block.setType(Material.AIR, false);
    Material type = block.getType();
    BlockShape blockShape = drill.outlineShapeOf(block.getWorld(), player, type, 0, 0, 0, 0);
    if (!blockShape.isEmpty() && IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
      System.out.println(type);
      System.out.println(blockShape);
    }
    assertTrue(blockShape.isEmpty());
  }

  @Test(
    testCode = "E",
    severity = Severity.ERROR
  )
  public void testDeviationFromActualCollision() {
    block.setType(Material.AIR, false);
    BlockShape blockShape = drill.collisionShapeOf(block.getWorld(), player, Material.DIAMOND_BLOCK, 0, 0, 0, 0);
    assertTrue(blockShape.isCubic());
  }

  @Test(
    testCode = "F",
    severity = Severity.WARNING
  )
  public void testDeviationFromActualOutline() {
    block.setType(Material.AIR, false);
    BlockShape blockShape = drill.outlineShapeOf(block.getWorld(), player, Material.DIAMOND_BLOCK, 0, 0, 0, 0);
    assertTrue(blockShape.isCubic());
  }

  @Test(
    testCode = "G",
    severity = Severity.ERROR
  )
  public void testComplexCollisionShape() {
    block.setType(Material.ANVIL, false);
    Material type = block.getType();
    BlockShape blockShape = drill.collisionShapeOf(block.getWorld(), player, type, 0, 0, 0, 0);
    assertFalse(blockShape.isCubic());
  }

  @After
  public void teardown() {
    priorMaterial.restore();
    UserRepository.unregisterUser(player);
  }
}
