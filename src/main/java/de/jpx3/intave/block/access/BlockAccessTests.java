package de.jpx3.intave.block.access;

import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.test.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

public final class BlockAccessTests extends Tests {
  private Block block;

  public BlockAccessTests() {
    super("BA");
  }

  @Before
  public void setup() {
    World world = Bukkit.getWorlds().get(0);
    block = world.getSpawnLocation().getBlock();
  }

  @Test(
    testCode = "A",
    severity = Severity.ERROR
  )
  public void testBlockTypes() {
    ItemStack diamondPickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
    BlockPosition blockPosition = new BlockPosition(block.getX(), block.getY(), block.getZ());
    Material type = block.getType();
    assertEquals(type, BlockAccess.global().typeOf(block));
    BlockAccess.global().variantIndexOf(block);
    BlockAccess.global().blockDamage(block.getWorld(), null, diamondPickaxe, blockPosition);
    BlockAccess.global().replacementPlace(block.getWorld(), null, blockPosition);
  }

  @After
  public void tearDown() {
  }
}
