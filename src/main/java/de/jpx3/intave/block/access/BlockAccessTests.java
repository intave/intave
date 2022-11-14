package de.jpx3.intave.block.access;

import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.test.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Set;

public final class BlockAccessTests extends Tests {
  private Block block, blockBelow;
  private BlockStorage priorMaterial, priorMaterialBelow;

  private final Set<Material> blacklistedMaterials = MaterialSearch.materialsThatContain("REDSTONE", "BED");

  public BlockAccessTests() {
    super("BA");
  }

  @Before
  public void setup() {
    World world = Bukkit.getWorlds().get(0);
    block = world.getBlockAt(0, 1, 0);
    blockBelow = world.getBlockAt(0, 0, 0);
    priorMaterial = BlockStorage.store(block);
    priorMaterialBelow = BlockStorage.store(blockBelow);
    block.setType(Material.AIR);
    blockBelow.setType(Material.BEDROCK);
  }

  @Test(
    testCode = "A",
    severity = Severity.ERROR
  )
  public void testBlockTypes() {
    for (Material value : Material.values()) {
      if (value.isBlock() && !blacklistedMaterials.contains(value)) {
        block.setType(value, false);
        assertEquals(value, block.getType());
        assertEquals(value, BlockAccess.global().typeOf(block));
      }
    }
  }

  @After
  public void tearDown() {
    priorMaterial.restore();
    priorMaterialBelow.restore();
  }
}
