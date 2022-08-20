package de.jpx3.intave.test;

import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

public final class TestWorld {
  private final WorldCreator worldCreator;
  private World world;

  private TestWorld(WorldCreator worldCreator) {
    this.worldCreator = worldCreator;
  }

  public void load() {
    world = Bukkit.createWorld(worldCreator);
  }

  public void erase() {
    Bukkit.unloadWorld(world, false);
    world = null;
  }

  public void reset() {
    erase();
    load();
  }

  public World bukkitWorld() {
    if (world == null) {
      throw new IllegalStateException("World not loaded");
    }
    return world;
  }

  public static TestWorld createLoaded() {
    TestWorld testWorld = create();
    testWorld.load();
    return testWorld;
  }

  public static TestWorld create() {
    WorldCreator worldCreator = new WorldCreator("IntaveTestingWorld");
    worldCreator.generator(new ChunkGenerator() {
      @Override
      public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        ChunkData chunkData = Bukkit.createChunkData(world);
        chunkData.setRegion(x << 4, 0, z << 4, (x << 4) + 15, 255, (z << 4) + 15, Material.AIR);
        chunkData.setRegion(x << 4, 0, z << 4, (x << 4) + 15, 1, (z << 4) + 15, Material.GRASS);
        return chunkData;
      }
    });
    worldCreator.type(WorldType.FLAT);
//    worldCreator.type(WorldType.CUSTOMIZED);

    return new TestWorld(worldCreator);
  }
}
