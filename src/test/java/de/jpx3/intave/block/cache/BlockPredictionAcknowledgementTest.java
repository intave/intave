package de.jpx3.intave.block.cache;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** ClientLevel / BlockStatePredictionHandler transitions verified in 1.21.11 and 26.1.2 through 26.3. */
class BlockPredictionAcknowledgementTest {
  private MultiChunkKeyBlockCache cache;

  @BeforeEach
  void setup() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
    cache = new MultiChunkKeyBlockCache(FakePlayerFactory.createPlayer(), new ShapeResolverPipeline() {
      @Override
      public BlockShape collisionShapeOf(World world, User user, Material type, int variant, int x, int y, int z) {
        return BlockShapes.originCube().contextualized(x, y, z);
      }

      @Override
      public BlockShape outlineShapeOf(World world, User user, Material type, int variant, int x, int y, int z) {
        return collisionShapeOf(world, user, type, variant, x, y, z);
      }
    });
  }

  @Test
  void acknowledgementRestoresTheOriginalStateAfterRepeatedPrediction() {
    predict(0, Material.AIR, Material.STONE, 10);
    predict(0, Material.STONE, Material.DIRT, 11);

    cache.moveClientSpeculationsToOverride(null, 10);
    assertTrue(cache.isClientSpeculatingAt(0, 64, 0));
    assertEquals(Material.DIRT, cache.typeAt(0, 64, 0));

    cache.moveClientSpeculationsToOverride(null, 11);
    assertFalse(cache.isClientSpeculatingAt(0, 64, 0));
    assertEquals(Material.AIR, cache.typeAt(0, 64, 0));
  }

  @Test
  void cumulativeAcknowledgementOnlyFinishesPredictionsUpToItsSequence() {
    predict(0, Material.AIR, Material.STONE, 10);
    predict(1, Material.AIR, Material.DIRT, 12);
    cache.moveClientSpeculationsToOverride(null, 11);
    assertFalse(cache.isClientSpeculatingAt(0, 64, 0));
    assertEquals(Material.AIR, cache.typeAt(0, 64, 0));
    assertTrue(cache.isClientSpeculatingAt(1, 64, 0));
    assertEquals(Material.DIRT, cache.typeAt(1, 64, 0));
  }

  @Test
  void serverUpdateWaitsForTheOriginalAcknowledgementDespiteNewerPlacements() {
    predict(0, Material.AIR, Material.STONE, 10);
    predict(1, Material.AIR, Material.DIRT, 12);
    assertTrue(cache.updateClientSpeculationValue(null, 0, 64, 0, Material.COBBLESTONE, 3));
    assertEquals(Material.STONE, cache.typeAt(0, 64, 0));

    cache.moveClientSpeculationsToOverride(null, 10);
    assertFalse(cache.isClientSpeculatingAt(0, 64, 0));
    assertEquals(Material.COBBLESTONE, cache.typeAt(0, 64, 0));
    assertEquals(3, cache.variantIndexAt(0, 64, 0));
    assertTrue(cache.isClientSpeculatingAt(1, 64, 0));
  }

  @Test
  void repeatedPredictionRetainsTheLatestServerUpdate() {
    predict(0, Material.AIR, Material.STONE, 10);
    assertTrue(cache.updateClientSpeculationValue(null, 0, 64, 0, Material.COBBLESTONE, 3));
    predict(0, Material.STONE, Material.DIRT, 11);

    cache.moveClientSpeculationsToOverride(null, 10);
    assertEquals(Material.DIRT, cache.typeAt(0, 64, 0));
    cache.moveClientSpeculationsToOverride(null, 11);
    assertEquals(Material.COBBLESTONE, cache.typeAt(0, 64, 0));
    assertEquals(3, cache.variantIndexAt(0, 64, 0));
  }

  @Test
  void serverUpdateAfterAcknowledgementIsNotRetainedAsAPrediction() {
    predict(0, Material.AIR, Material.STONE, 10);
    cache.moveClientSpeculationsToOverride(null, 10);
    assertFalse(cache.updateClientSpeculationValue(null, 0, 64, 0, Material.DIRT, 0));
    cache.override(null, 0, 64, 0, Material.DIRT, 0, "UPDATE");
    cache.moveClientSpeculationsToOverride(null, 10);
    assertEquals(Material.DIRT, cache.typeAt(0, 64, 0));
    assertFalse(cache.isClientSpeculatingAt(0, 64, 0));
  }

  @Test
  void acknowledgementReleasesThePlacementLock() {
    predict(0, Material.AIR, Material.STONE, 10);
    cache.moveClientSpeculationsToOverride(null, 10);
    cache.invalidateOverridesInBounds(0, 16, 0, 16);
    assertFalse(cache.currentlyInOverride(0, 64, 0));
  }

  @Test
  void undoingPredictionPreventsLaterAcknowledgementsFromReplacingTheBlock() {
    predict(0, Material.AIR, Material.STONE, 10);
    cache.undoClientSpeculation(null, 0, 64, 0);
    cache.override(null, 0, 64, 0, Material.DIRT, 0);
    cache.moveClientSpeculationsToOverride(null, 10);
    assertEquals(Material.DIRT, cache.typeAt(0, 64, 0));
    assertFalse(cache.isClientSpeculatingAt(0, 64, 0));
  }

  @Test
  void clearingTheWorldPreventsLateAcknowledgementsFromRestoringOldBlocks() {
    predict(0, Material.AIR, Material.STONE, 10);
    cache.invalidateAll();
    assertFalse(cache.isClientSpeculatingAt(0, 64, 0));
    cache.override(null, 0, 64, 0, Material.DIRT, 0);
    cache.moveClientSpeculationsToOverride(null, 10);
    assertEquals(Material.DIRT, cache.typeAt(0, 64, 0));
  }

  private void predict(int x, Material previous, Material predicted, int sequence) {
    cache.setClientSpeculationValue(null, x, 64, 0, previous, 0, sequence);
    cache.override(null, x, 64, 0, predicted, 0, "PLACE");
    cache.lockOverride(x, 64, 0);
  }
}
