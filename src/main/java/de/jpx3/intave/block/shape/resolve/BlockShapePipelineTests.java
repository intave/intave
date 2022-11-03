package de.jpx3.intave.block.shape.resolve;

import com.google.common.collect.ImmutableSet;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.test.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.material.MaterialData;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BlockShapePipelineTests extends Tests {
  private Player player;
  private SimpleDrill drill;
  private ShapeResolverPipeline head;
  private World world;
  private Block block;
  private BlockStorage previousMaterialData;

  public BlockShapePipelineTests() {
    super("BSPT");
  }

  @Before
  public void before() {
    world = Bukkit.getWorlds().get(0);
    block = world.getBlockAt(0, 0, 0);
    previousMaterialData = BlockStorage.store(block);
    player = FakePlayerFactory.createPlayer();
  }

  @Test(
    testCode = "A",
    severity = Severity.WARNING
  )
  public void testPipelineSequence() {
    drill = new SimpleDrill();
    head = ShapeResolver.createPipelineFor(drill);
    List<Material> types = Arrays.asList(Material.DIAMOND_BLOCK, Material.AIR, Material.BEDROCK, Material.WATER, Material.DIAMOND_BLOCK);
    for (Material type : types) {
      head.collisionShapeOf(world, player, type, 0, 0, 0, 0);
      head.outlineShapeOf(world, player, type, 0, 0, 0, 0);
    }
    drill.verifyEnd();
  }

  @After
  public void after() {
    previousMaterialData.restore();
  }

  private static final class SimpleDrill implements ShapeResolverPipeline {
    private final Set<Material> visitedCollisionCubicMaterials = new HashSet<>();
    private final Set<Material> visitedCollisionNonCubicMaterials = new HashSet<>();
    private final Set<Material> visitedOutlineCubicMaterials = new HashSet<>();
    private final Set<Material> visitedOutlineNonCubicMaterials = new HashSet<>();
    private final Set<Material> forbiddenMaterials = ImmutableSet.of(Material.AIR, Material.WATER);

    @Override
    public BlockShape collisionShapeOf(World world, Player player, Material material, int variantIndex, int posX, int posY, int posZ) {
      if (forbiddenMaterials.contains(material)) {
        throw new IllegalArgumentException("Material " + material + " is forbidden");
      }
      BlockShape shape;
      if (material == Material.DIAMOND_BLOCK) {
        shape = BlockShapes.originCube();
      } else if (material == Material.BEDROCK) {
        // anything but empty or cubic, just complex
        shape = new BoundingBox(0.25, 0.25, 0.25, 0.75, 0.75, 0.75);
      } else {
        shape = BlockShapes.emptyShape();
      }
      if (shape.isCubic()) {
        if (visitedCollisionCubicMaterials.contains(material)) {
          throw new IllegalArgumentException("Material " + material + " is already visited");
        }
        visitedCollisionCubicMaterials.add(material);
      } else {
        visitedCollisionNonCubicMaterials.add(material);
      }
      return shape;
    }

    @Override
    public BlockShape outlineShapeOf(World world, Player player, Material type, int variantIndex, int posX, int posY, int posZ) {
      if (forbiddenMaterials.contains(type)) {
        throw new IllegalArgumentException("Material " + type + " is forbidden");
      }
      BlockShape shape;
      if (type == Material.DIAMOND_BLOCK) {
        shape = BlockShapes.originCube();
      } else if (type == Material.BEDROCK) {
        // anything but empty or cubic, just complex
        shape = new BoundingBox(0.25, 0.25, 0.25, 0.75, 0.75, 0.75);
      } else {
        shape = BlockShapes.emptyShape();
      }
      if (shape.isCubic()) {
        if (visitedOutlineCubicMaterials.contains(type)) {
          throw new IllegalArgumentException("Material " + type + " is already visited");
        }
        visitedOutlineCubicMaterials.add(type);
      } else {
        visitedOutlineNonCubicMaterials.add(type);
      }
      return shape;
    }

    public void verifyEnd() {
      if (visitedCollisionNonCubicMaterials.size() != 1) {
        throw new IllegalStateException("Expected 1 non-cubic material lookup here, got " + visitedCollisionNonCubicMaterials.size());
      }
      if (visitedOutlineNonCubicMaterials.size() != 1) {
        throw new IllegalStateException("Expected 1 non-cubic material lookup here, got " + visitedOutlineNonCubicMaterials.size());
      }
    }
  }
}
