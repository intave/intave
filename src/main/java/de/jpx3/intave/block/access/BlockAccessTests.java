package de.jpx3.intave.block.access;

import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.test.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public final class BlockAccessTests extends Tests {
  private Block block, blockBelow;
  private BlockStorage priorMaterial, priorMaterialBelow;
  private final Set<Material> blacklistedMaterials = MaterialSearch.materialsThatContain("REDSTONE", "BED", "SOIL", "GRASS_PATH", "EGG");

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
    ItemStack diamondPickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
    BlockPosition blockPosition = new BlockPosition(0, 1, 0);

    for (Material value : Material.values()) {
      if (value.isBlock() && !blacklistedMaterials.contains(value)) {
        block.setType(value, false);
        if (block.getType() == Material.OBSIDIAN) {
          // oh yes that happens
          continue;
        }
        assertEquals(value, block.getType());
        assertEquals(value, BlockAccess.global().typeOf(block));
        try {
          BlockAccess.global().blockDamage(block.getWorld(), null, diamondPickaxe, blockPosition);
        } catch (NullPointerException e) {
          // pass test
        }
        try {
          BlockAccess.global().replacementPlace(block.getWorld(), null, blockPosition);
        } catch (NullPointerException e) {
          // pass test
        }
      }
    }
  }

  @After
  public void tearDown() {
    priorMaterial.restore();
    priorMaterialBelow.restore();
  }
}
