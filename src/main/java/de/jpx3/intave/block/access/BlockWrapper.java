package de.jpx3.intave.block.access;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.user.User;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

public final class BlockWrapper {
  public static void setup() {
  }

  public static Block emit(User user, Block input) {
    return InternalWrapper.emit(user, input.getWorld(), input.getX(), input.getY(), input.getZ());
  }

  public static class InternalWrapper {
    public static Block emit(User user, World world, int x, int y, int z) {
      return new VolatileBlock(user, world, x, y, z);
    }
  }

  public static class VolatileBlock extends FakeFallbackBlock {
    private final User user;
    private final World world;
    private final int x;
    private final int y;
    private final int z;
    private final Location location;

    public VolatileBlock(User user, World world, int x, int y, int z) {
      super(world);
      this.user = user;
      this.world = world;
      this.x = x;
      this.y = y;
      this.z = z;
      this.location = new Location(world, x, y, z);
    }

    @Override
    public @NotNull Material getType() {
      return VolatileBlockAccess.typeAccess(user, location);
    }

    @Override
    public byte getData() {
      return (byte) VolatileBlockAccess.variantIndexAccess(user, location);
    }

    @Override
    public Block getRelative(int modX, int modY, int modZ) {
      return InternalWrapper.emit(user, world, x + modX, y + modY, z + modZ);
    }

    @Override
    public Block getRelative(BlockFace face) {
      return getRelative(face, 1);
    }

    @Override
    public Block getRelative(BlockFace face, int distance) {
      return getRelative(face.getModX() * distance, face.getModY() * distance, face.getModZ() * distance);
    }

    @Override
    public @NotNull Location getLocation() {
      return location.clone();
    }

    @Override
    public Location getLocation(Location target) {
      if (target == null) {
        return getLocation();
      }
      target.setWorld(world);
      target.setX(x);
      target.setY(y);
      target.setZ(z);
      target.setYaw(0.0F);
      target.setPitch(0.0F);
      return target;
    }

    @Override
    public @NotNull World getWorld() {
      return world;
    }

    @Override
    public int getX() {
      return x;
    }

    @Override
    public int getY() {
      return y;
    }

    @Override
    public int getZ() {
      return z;
    }

    public @NotNull BlockData getBlockData() {
      Material material = getType();
      int variant = VolatileBlockAccess.variantIndexAccess(user, location);
      Object rawVariant = BlockVariantRegister.rawVariantOf(material, variant);
      if (rawVariant instanceof WrappedBlockState) {
        return SpigotConversionUtil.toBukkitBlockData((WrappedBlockState) rawVariant);
      }
      return createBlockData(material);
    }

    @Override
    public byte getLightLevel() {
      return world.getBlockAt(x, y, z).getLightLevel();
    }

    @Override
    public byte getLightFromSky() {
      return world.getBlockAt(x, y, z).getLightFromSky();
    }

    @Override
    public byte getLightFromBlocks() {
      return world.getBlockAt(x, y, z).getLightFromBlocks();
    }

    private static BlockData createBlockData(Material material) {
      try {
        return (BlockData) Bukkit.class.getMethod("createBlockData", Material.class).invoke(null, material);
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Unable to create block data for " + material, exception);
      }
    }
  }
}
