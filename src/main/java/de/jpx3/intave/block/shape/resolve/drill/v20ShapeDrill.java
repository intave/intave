package de.jpx3.intave.block.shape.resolve.drill;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.resolve.ShapeResolutionFailure;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.klass.rewrite.PatchyTranslateParameters;
import de.jpx3.intave.library.asm.Type;
import net.minecraft.core.BlockPosition;
import net.minecraft.server.level.ChunkProviderServer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapes;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@PatchyAutoTranslation
public final class v20ShapeDrill extends AbstractShapeDrill {
  @Override
  @PatchyAutoTranslation
  public BlockShape collisionShapeOf(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, blockState);
    if (blockData == null) {
      return BlockShapes.emptyShape();
    }
    IBlockAccess blockAccess = chunkAccessOf(world, posX, posZ);
    try {
      VoxelShape collisionShape = blockData.getCollisionShape(blockAccess, blockPositionOf(posX, posY, posZ));
      return shapeFromVoxel(collisionShape, posX, posY, posZ);
    } catch (Throwable throwable) {
      // Never answer "full cube" here: the pipeline caches the result per block
      // variant, so guessing turns one failed lookup into a permanently wrong shape.
      throw new ShapeResolutionFailure("collision shape of " + type + "[" + blockState + "]", throwable);
    }
  }

  @Override
  @PatchyAutoTranslation
  public BlockShape outlineShapeOf(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, blockState);
    if (blockData == null) {
      return BlockShapes.emptyShape();
    }
    IBlockAccess blockAccess = chunkAccessOf(world, posX, posZ);
    try {
      VoxelShape shape = blockData.getShape(blockAccess, blockPositionOf(posX, posY, posZ));
      return shapeFromVoxel(shape, posX, posY, posZ);
    } catch (Throwable throwable) {
      throw new ShapeResolutionFailure("outline shape of " + type + "[" + blockState + "]", throwable);
    }
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private BlockPosition blockPositionOf(int posX, int posY, int posZ) {
    return new BlockPosition(posX, posY, posZ);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private BlockShape shapeFromVoxel(VoxelShape shape, int posX, int posY, int posZ) {
    // should never happen, but just in case
    if (shape == null) {
      return BlockShapes.emptyShape();
    }
    // check if shape is static empty
    if (VoxelShapes.a() == shape) {
      return BlockShapes.emptyShape();
    }
    // check if shape is static cube
    if (VoxelShapes.b() == shape) {
      return BlockShapes.cubeAt(posX, posY, posZ);
    }
    // convert complex blocks to native BBs
    return translateWithOffset(shape.toList(), posX, posY, posZ);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private IBlockAccess chunkAccessOf(World world, int posX, int posZ) {
    if (Synchronizer.onFolia()) {
      // Fetching the chunk is a chunk-system read, and region-threaded servers only
      // allow those on the thread owning the region -- shapes, however, are resolved
      // wherever a movement or interaction packet is handled. Since 1.13 a block's
      // shape is fully determined by its state (wall/fence connections, slab and
      // trapdoor halves, stair shapes are all stored in the state itself), and vanilla
      // serves it straight from the block state's shape cache without touching the
      // level, so resolving without one returns the same shape for every block that
      // does not declare a dynamic shape.
      //
      // Pass the empty accessor rather than null: the handful of blocks whose shape
      // does read the level only read it to find their block entity, and this is the
      // very accessor vanilla builds its own static shape cache with, so they answer
      // with the shape they fall back to when there is no block entity -- a full cube
      // for a shulker box (its closed shape), nothing for a moving piston. Passing
      // null instead made those throw, and the rescue pipe then answered with a
      // neutral 0.25-0.75 box: a player standing on a shulker box was standing on a
      // surface 0.25 too low in our model, so we had them falling while they stood
      // still.
      return emptyBlockAccess();
    }
    WorldServer handle = ((CraftWorld) world).getHandle();
    return findChunk(handle.getChunkProvider(), posX >> 4, posZ >> 4);//handle.getChunkProvider().c(posX >> 4, posZ >> 4);
  }

  private static Object emptyBlockAccessInstance;
  private static boolean emptyBlockAccessResolved;

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private IBlockAccess emptyBlockAccess() {
    if (!emptyBlockAccessResolved) {
      emptyBlockAccessResolved = true;
      try {
        Field instance = Lookup.serverField("EmptyBlockGetter", "INSTANCE");
        instance.setAccessible(true);
        emptyBlockAccessInstance = instance.get(null);
      } catch (Throwable throwable) {
        // a server without it just gets the old null behaviour: shapes that need a
        // block entity throw and are answered by the rescue pipe
        emptyBlockAccessInstance = null;
      }
    }
    return (IBlockAccess) emptyBlockAccessInstance;
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private LightChunk findChunk(ChunkProviderServer server, int x, int z) {
//    return (LightChunk) server.c(x, z);
    Class<?> chunk = Lookup.serverClass("LightChunk");
    Method providerMethod = Lookup.serverMethod("ChunkProviderServer", "c", new Type[]{Type.INT_TYPE, Type.INT_TYPE}, Type.getType(chunk));
    try {
      return (LightChunk) providerMethod.invoke(server, x, z);
    } catch (IllegalAccessException | InvocationTargetException ignored) {
    }
    return null;
  }
}