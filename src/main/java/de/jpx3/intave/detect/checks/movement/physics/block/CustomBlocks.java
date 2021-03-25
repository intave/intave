package de.jpx3.intave.detect.checks.movement.physics.block;

import com.comphenix.protocol.utility.MinecraftVersion;
import com.google.common.collect.Lists;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.tools.annotate.Nullable;
import de.jpx3.intave.user.User;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CustomBlocks {
  private final static MinecraftVersion MINECRAFT_VERSION = ProtocolLibAdapter.serverVersion();
  private final List<CustomBlock> blocks = Lists.newArrayList();
  private final Map<Material, CustomBlock> blockAccessCache = new HashMap<>();

  public CustomBlocks() {
    try {
      loadBlocks();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void loadBlocks() {
    loadBlock(CustomBlockBed.class);
    loadBlock(CustomBlockSlime.class);
    loadBlock(CustomBlockWeb.class);
    loadBlock(CustomBlockSoulSand.class);
    loadBlock(CustomBlockBerryBush.class);
    loadBlock(CustomBlockWeb.class);
  }

  private void loadBlock(Class<? extends CustomBlock> blockClass) {
    try {
      CustomBlock block = blockClass.newInstance();
      block.setup(MINECRAFT_VERSION);
      if (block.supportedOnServerVersion()) {
        for (Material material : block.materials()) {
          blockAccessCache.put(material, block);
        }
      }
      blocks.add(block);
    } catch (InstantiationException | IllegalAccessException exception) {
      exception.printStackTrace();
    }
  }

  @Nullable
  public Vector entityCollision(
    User user,
    Material material,
    Location location, Location from,
    double motionX, double motionY, double motionZ
  ) {
    CustomBlock collision = findPotentialCollision(material);
    return collision != null ? collision.entityCollidedWithBlock(user, location, from, motionX, motionY, motionZ) : null;
  }

  @Nullable
  public Vector entityCollision(
    User user,
    Material material,
    double motionX, double motionY, double motionZ
  ) {
    CustomBlock collision = findPotentialCollision(material);
    return collision != null ? collision.entityCollidedWithBlock(user, motionX, motionY, motionZ) : null;
  }

  @Nullable
  public Vector blockLanded(
    User user,
    Material material,
    double motionX, double motionY, double motionZ
  ) {
    CustomBlock collision = findPotentialCollision(material);
    return collision != null ? collision.landed(user, motionX, motionY, motionZ) : null;
  }

  @Nullable
  public Vector speedFactor(
    User user,
    Material material,
    double motionX, double motionY, double motionZ
  ) {
    CustomBlock collision = findPotentialCollision(material);
    return collision != null ? collision.speedFactor(user, motionX, motionY, motionZ) : null;
  }

  public void fallenUpon(User user, Material material) {
    CustomBlock collision = findPotentialCollision(material);
    if (collision != null) {
      collision.fallenUpon(user);
    }
  }

  private CustomBlock findPotentialCollision(Material material) {
    return blockAccessCache.get(material);
  }
}