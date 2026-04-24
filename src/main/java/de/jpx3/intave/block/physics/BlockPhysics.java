package de.jpx3.intave.block.physics;

import de.jpx3.intave.version.MinecraftVersion;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

public final class BlockPhysics {
  private static final MinecraftVersion MINECRAFT_VERSION = MinecraftVersion.getCurrentVersion();
  private static final Map<Material, BlockPhysic> materialLookup = new HashMap<>();

  public static void setup() {
    setup(BedPhysics.class);
    setup(SlimePhysics.class);
    setup(WebPhysics.class);
    setup(SoulSandPhysics.class);
    setup(BerryBushPhysics.class);
    setup(HoneyPhysics.class);
    setup(FluidPhysics.class);
    setup(BubbleColumnPhysics.class);
    setup(PowderSnowPhysics.class);
  }

  private static void setup(Class<? extends BlockPhysic> blockPhysicClass) {
    try {
      BlockPhysic blockPhysic = blockPhysicClass.newInstance();
      blockPhysic.setupFor(MINECRAFT_VERSION);
      if (blockPhysic.supportedOnServerVersion()) {
        for (Material material : blockPhysic.applicableMaterials()) {
          materialLookup.put(material, blockPhysic);
        }
      }
    } catch (InstantiationException | IllegalAccessException exception) {
      exception.printStackTrace();
    }
  }

  @Nullable
  public static Motion entityCollision(
    User user,
    Material material,
    Location location, Location from,
    double motionX, double motionY, double motionZ
  ) {
    BlockPhysic collision = physicLookup(material);
    if (collision != null) {
      return collision.entityCollidedWithBlock(user, location, from, motionX, motionY, motionZ);
    }
    return null;
  }

  @Nullable
  public static Motion entityCollision(
    User user,
    Material material,
    double motionX, double motionY, double motionZ
  ) {
    BlockPhysic collision = physicLookup(material);
    return collision != null ? collision.entityCollidedWithBlock(user, motionX, motionY, motionZ) : null;
  }

  @Nullable
  public static Motion blockLanded(
    User user,
    Material material,
    double motionX, double motionY, double motionZ
  ) {
    BlockPhysic collision = physicLookup(material);
    return collision != null ? collision.landed(user, motionX, motionY, motionZ) : null;
  }

  public static void fallenUpon(User user, Material material) {
    BlockPhysic collision = physicLookup(material);
    if (collision != null) {
      collision.fallenUpon(user);
    }
  }

  private static BlockPhysic physicLookup(Material material) {
    return materialLookup.get(material);
  }
}
