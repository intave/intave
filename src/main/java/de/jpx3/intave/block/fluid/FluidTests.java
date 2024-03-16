package de.jpx3.intave.block.fluid;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.cache.BlockCaches;
import de.jpx3.intave.test.BlockStorage;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.Test;
import de.jpx3.intave.test.Tests;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;

import static de.jpx3.intave.test.Severity.ERROR;
import static org.bukkit.Material.LAVA;
import static org.bukkit.Material.WATER;

public final class FluidTests extends Tests {
  private BlockStorage blockStorage;

  public FluidTests() {
    super("FLD");
  }

  @Test(
    testCode = "BASIC",
    severity = ERROR
  )
  public void testWaterBasic() {
    Material[] mustBeLiquid = new Material[] {WATER, LAVA, Material.getMaterial("STATIONARY_LAVA"), Material.getMaterial("STATIONARY_WATER")};
    Material[] mustNotBeLiquid = new Material[] {Material.AIR, Material.WATER_BUCKET, Material.DIAMOND_AXE, Material.STONE, Material.getMaterial("ELYTRA")};

    for (Material material : mustBeLiquid) {
      if (material != null && !Fluids.isFluid(material, 0)) {
        fail(material + " is not a liquid?");
      }
    }
    for (Material material : mustNotBeLiquid) {
      if (material != null && Fluids.isFluid(material, 0)) {
        fail(material + " is a liquid?");
      }
    }
  }

  @Test(
    testCode = "level",
    severity = ERROR
  )
  public void testWaterLevel() {
    World world = Bukkit.getWorlds().get(0);
    Block block = world.getBlockAt(0, 4, 0);
    block.setType(WATER, false);
    blockStorage = BlockStorage.store(block);

    if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      // set water level to 3
      BlockState state = block.getState();

      if (state instanceof Levelled) {
        ((Levelled) state).setLevel(3);
      } else {
        // test not conductable
        blockStorage.restore();
        return;
      }
    } else {
      block.setData((byte) 3);
    }

    Player player = FakePlayerFactory.createPlayer((s, objects) -> {
      if (s.equals("getWorld")) {
        return world;
      }
      return null;
    });
    BlockCache blockStateCache = BlockCaches.passthroughCacheWithNativeDrill(player);
    User user = UserFactory.createTestUserFor(player, s -> {
      if (s.equals("protocolVersion")) {
        return 477;
      }
      if (s.equals("blockCache")) {
        return blockStateCache;
      }
      return null;
    });
    Fluid fluid = Fluids.fluidAt(user, block.getLocation());
    if (!fluid.isOfWater()) {
      fail("Water is not water?");
    }
    if (fluid.level() != 3) {
      fail("Water level is not 3, expected 3");
    }
    blockStorage.restore();
  }
}
