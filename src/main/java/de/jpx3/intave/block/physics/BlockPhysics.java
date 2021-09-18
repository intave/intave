package de.jpx3.intave.block.physics;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.user.User;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public final class BlockPhysics {
  private final static MinecraftVersion MINECRAFT_VERSION = MinecraftVersion.getCurrentVersion();
  private final static Map<Material, BlockPhysic> blockAccessCache = new HashMap<>();

  public static void setup() {
    setup(BedPhysics.class);
    setup(SlimePhysics.class);
    setup(WebPhysics.class);
    setup(SoulSandPhysics.class);
    setup(BerryBushPhysics.class);
    setup(HoneyPhysics.class);
    setup(WebPhysics.class);
    setup(FluidPhysics.class);
    setup(BubbleColumnPhysics.class);
  }

  private static void setup(Class<? extends BlockPhysic> blockClass) {
    try {
      BlockPhysic block = blockClass.newInstance();
      block.setup(MINECRAFT_VERSION);
      if (block.supportedOnServerVersion()) {
        for (Material material : block.materials()) {
          blockAccessCache.put(material, block);
        }
      }
    } catch (InstantiationException | IllegalAccessException exception) {
      exception.printStackTrace();
    }
  }

  @Nullable
  public static Vector entityCollision(
    User user,
    Material material,
    Location location, Location from,
    double motionX, double motionY, double motionZ
  ) {
    BlockPhysic collision = findCustomPhysicsEntry(material);
    return collision != null ? collision.entityCollidedWithBlock(user, location, from, motionX, motionY, motionZ) : null;
  }

  @Nullable
  public static Vector entityCollision(
    User user,
    Material material,
    double motionX, double motionY, double motionZ
  ) {
    BlockPhysic collision = findCustomPhysicsEntry(material);
    return collision != null ? collision.entityCollidedWithBlock(user, motionX, motionY, motionZ) : null;
  }

  @Nullable
  public static Vector blockLanded(
    User user,
    Material material,
    double motionX, double motionY, double motionZ
  ) {
    BlockPhysic collision = findCustomPhysicsEntry(material);
    return collision != null ? collision.landed(user, motionX, motionY, motionZ) : null;
  }

  public static void fallenUpon(User user, Material material) {
    BlockPhysic collision = findCustomPhysicsEntry(material);
    if (collision != null) {
      collision.fallenUpon(user);
    }
  }

  private static BlockPhysic findCustomPhysicsEntry(Material material) {
    return blockAccessCache.get(material);
  }
}