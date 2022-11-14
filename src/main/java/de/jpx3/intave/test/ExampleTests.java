package de.jpx3.intave.test;

import de.jpx3.intave.block.access.BlockAccess;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class ExampleTests extends Tests {
  private Block targetBlock;
  private Material priorMaterial;

  public ExampleTests() {
    super("EX");
  }

  @Before
  public void setup() {
    World world = Bukkit.getWorlds().get(0);
    targetBlock = world.getBlockAt(0, 0, 0);
    priorMaterial = targetBlock.getType();
  }

  @Test(
    testCode = "MAT_CMP",
    severity = Severity.ERROR
  )
  public void test() {
    targetBlock.setType(Material.DIAMOND_BLOCK);
    Material resolvedType = BlockAccess.global().typeOf(targetBlock);
    assertEquals(Material.DIAMOND_BLOCK, resolvedType);
  }

  @Test(
    testCode = "MAT2_CMP",
    severity = Severity.ERROR
  )
  public void test2() {
    targetBlock.setType(Material.DIAMOND_BLOCK);
    Material resolvedType = BlockAccess.global().typeOf(targetBlock);
    assertEquals(Material.DIAMOND_BLOCK, resolvedType);
  }

  @After
  public void teardown() {
    targetBlock.setType(priorMaterial);
  }
}
