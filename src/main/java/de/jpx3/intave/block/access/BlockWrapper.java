package de.jpx3.intave.block.access;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.clazz.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.clazz.rewrite.PatchyLoadingInjector;
import de.jpx3.intave.clazz.rewrite.PatchyTranslateParameters;
import de.jpx3.intave.user.User;
import net.minecraft.server.v1_13_R2.BlockPosition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_8_R3.CraftChunk;
import org.jetbrains.annotations.NotNull;

public final class BlockWrapper {
  public static void setup() {
    ClassLoader classLoader = BlockWrapper.class.getClassLoader();
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.block.access.BlockWrapper$InternalWrapper");
    if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.block.access.BlockWrapper$v13WrappedBlock");
    } else {
      PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.block.access.BlockWrapper$v8WrappedBlock");
    }

  }

  public static Block emit(User user, Block input) {
    return InternalWrapper.emit(user, input.getWorld(), input.getX(), input.getY(), input.getZ());
  }

  @PatchyAutoTranslation
  public static class InternalWrapper {
    @PatchyAutoTranslation
    public static Block emit(User user, World world, int x, int y, int z) {
      if (MinecraftVersions.VER1_13_0.atOrAbove()) {
        return new v13WrappedBlock(user, x, y, z);
      } else {
        CraftChunk chunkAt = (CraftChunk) world.getChunkAt(x >> 4, z >> 4);
        return new v8WrappedBlock(user, chunkAt, x, y, z);
      }
    }
  }

  @PatchyAutoTranslation
  public static class v8WrappedBlock extends org.bukkit.craftbukkit.v1_8_R3.block.CraftBlock {
    private final User user;
    private final Location location;

    @PatchyAutoTranslation
    @PatchyTranslateParameters
    public v8WrappedBlock(User user, org.bukkit.craftbukkit.v1_8_R3.CraftChunk chunk, int x, int y, int z) {
      super(chunk, x, y, z);
      this.user = user;
    }

    {
      location = getLocation();
    }

    @Override
    public Material getType() {
      return VolatileBlockAccess.typeAccess(user, location);
    }
  }

  @PatchyAutoTranslation
  public static class v13WrappedBlock extends org.bukkit.craftbukkit.v1_13_R2.block.CraftBlock {
    private final User user;
    private final Location location;

    @PatchyAutoTranslation
    @PatchyTranslateParameters
    public v13WrappedBlock(User user, int x, int y, int z) {
      super(((CraftWorld) user.player().getWorld()).getHandle(), new BlockPosition(x, y, z));
      this.user = user;
      location = new Location(
        user.player().getWorld(),
        x, y, z
      );
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
    public @NotNull Location getLocation() {
      return location;
    }

    @Override
    public @NotNull World getWorld() {
      return location.getWorld();
    }

    @Override
    @PatchyAutoTranslation
    @PatchyTranslateParameters
    public @NotNull BlockData getBlockData() {
      return CraftBlockData.fromData(getNMS());
    }

    @Override
    public byte getLightLevel() {
      return 0;
    }

    @Override
    public byte getLightFromSky() {
      return 0;
    }

    @Override
    public byte getLightFromBlocks() {
      return 0;
    }

    @PatchyAutoTranslation
    @PatchyTranslateParameters
    public net.minecraft.server.v1_13_R2.IBlockData getNMS() {
      return (net.minecraft.server.v1_13_R2.IBlockData) BlockVariantAccess.nativeVariantAccess(this);
    }
  }
}
