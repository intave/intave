package de.jpx3.intave.block.variant;

import de.jpx3.intave.test.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;

public final class BlockVariantTests extends IntegrationTests {
  private Block block, blockBelow;
  private BlockStorage priorMaterial, priorMaterialBelow;

  public BlockVariantTests() {
    super("BV");
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
  public void highestVariantTesting() {
    Optional<Material> max = Arrays.stream(Material.values()).filter(Material::isBlock)
      .max(Comparator.comparing(material -> BlockVariantRegister.variantIdsOf(material).size()));
    if (max.isPresent()) {
      Material material = max.get();
      Set<BlockVariant> variants = new HashSet<>();
      for (int i : BlockVariantRegister.variantIdsOf(material)) {
        BlockVariant blockVariant = BlockVariantRegister.variantOf(material, i);
        assertEquals(blockVariant.index(), i);
        assertDoesNotContain(variants, blockVariant);
        variants.add(blockVariant);
      }
      BlockVariantRegister.invalidateShadowedVariantCache();
    }
  }

  @After
  public void tearDown() {
    priorMaterial.restore();
    priorMaterialBelow.restore();
  }
}
