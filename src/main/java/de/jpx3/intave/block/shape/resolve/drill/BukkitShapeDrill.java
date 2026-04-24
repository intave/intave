package de.jpx3.intave.block.shape.resolve.drill;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.BoundingBox;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class BukkitShapeDrill extends AbstractShapeDrill {
  private static final Method BLOCK_DATA_COLLISION_SHAPE = method(BlockData.class, "getCollisionShape", Location.class);
  private static final Method BLOCK_COLLISION_SHAPE = method(Block.class, "getCollisionShape");
  private static final Method BLOCK_BOUNDING_BOX = method(Block.class, "getBoundingBox");
  private static final Method BUKKIT_CREATE_BLOCK_DATA = method(Bukkit.class, "createBlockData", Material.class);

  @Override
  public BlockShape collisionShapeOf(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    BlockData blockData = blockDataOf(type, blockState);
    if (blockData == null) {
      return BlockShapes.emptyShape();
    }
    BlockShape paperShape = collisionShapeOfBlockData(world, blockData, posX, posY, posZ);
    if (paperShape != null) {
      return paperShape;
    }
    Block block = world.getBlockAt(posX, posY, posZ);
    BlockShape blockShape = collisionShapeOfBlock(block, posX, posY, posZ);
    return blockShape == null ? fallbackOutline(type, posX, posY, posZ) : blockShape;
  }

  @Override
  public BlockShape outlineShapeOf(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    Block block = world.getBlockAt(posX, posY, posZ);
    if (block.getType() == type) {
      BlockShape outlineShape = outlineShapeOfBlock(block, posX, posY, posZ);
      if (outlineShape != null) {
        return outlineShape;
      }
    }
    BlockShape collisionShape = collisionShapeOf(world, player, type, blockState, posX, posY, posZ);
    return collisionShape.isEmpty() ? fallbackOutline(type, posX, posY, posZ) : collisionShape;
  }

  private BlockShape collisionShapeOfBlock(Block block, int posX, int posY, int posZ) {
    if (BLOCK_COLLISION_SHAPE == null) {
      return null;
    }
    try {
      Object voxelShape = BLOCK_COLLISION_SHAPE.invoke(block);
      return shapeFromVoxelShape(voxelShape, posX, posY, posZ);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
      return null;
    }
  }

  private BlockShape outlineShapeOfBlock(Block block, int posX, int posY, int posZ) {
    if (BLOCK_BOUNDING_BOX == null) {
      return null;
    }
    try {
      return shapeFromBukkitBox((org.bukkit.util.BoundingBox) BLOCK_BOUNDING_BOX.invoke(block), posX, posY, posZ);
    } catch (IllegalAccessException | InvocationTargetException exception) {
      return null;
    }
  }

  private BlockShape collisionShapeOfBlockData(World world, BlockData blockData, int posX, int posY, int posZ) {
    if (BLOCK_DATA_COLLISION_SHAPE == null) {
      return null;
    }
    try {
      Location location = new Location(world, posX, posY, posZ);
      Object voxelShape = BLOCK_DATA_COLLISION_SHAPE.invoke(blockData, location);
      return shapeFromVoxelShape(voxelShape, posX, posY, posZ);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
      return null;
    }
  }

  private BlockShape shapeFromVoxelShape(Object voxelShape, int posX, int posY, int posZ) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method boundingBoxes = voxelShape.getClass().getMethod("getBoundingBoxes");
    @SuppressWarnings("unchecked")
    Collection<org.bukkit.util.BoundingBox> boxes = (Collection<org.bukkit.util.BoundingBox>) boundingBoxes.invoke(voxelShape);
    return shapeFromBukkitVoxel(boxes, posX, posY, posZ);
  }

  private BlockData blockDataOf(Material type, int blockState) {
    Object rawVariant = BlockVariantRegister.rawVariantOf(type, blockState);
    if (rawVariant instanceof BlockData) {
      return (BlockData) rawVariant;
    }
    if (rawVariant instanceof WrappedBlockState) {
      return SpigotConversionUtil.toBukkitBlockData((WrappedBlockState) rawVariant);
    }
    return defaultBlockData(type);
  }

  private BlockData defaultBlockData(Material type) {
    if (BUKKIT_CREATE_BLOCK_DATA == null) {
      return null;
    }
    try {
      return (BlockData) BUKKIT_CREATE_BLOCK_DATA.invoke(null, type);
    } catch (IllegalAccessException | InvocationTargetException exception) {
      return null;
    }
  }

  private BlockShape fallbackOutline(Material type, int posX, int posY, int posZ) {
    return type.isSolid() ? BlockShapes.cubeAt(posX, posY, posZ) : BlockShapes.emptyShape();
  }

  private BlockShape shapeFromBukkitVoxel(Collection<org.bukkit.util.BoundingBox> boxes, int posX, int posY, int posZ) {
    if (boxes == null || boxes.isEmpty()) {
      return BlockShapes.emptyShape();
    }
    List<BoundingBox> translated = new ArrayList<>(boxes.size());
    for (org.bukkit.util.BoundingBox box : boxes) {
      translated.add(shapeFromBukkitBox(box, posX, posY, posZ));
    }
    return BlockShapes.mergeBoxes(translated);
  }

  private BoundingBox shapeFromBukkitBox(org.bukkit.util.BoundingBox box, int posX, int posY, int posZ) {
    BoundingBox translated = BoundingBox.fromBounds(
      box.getMinX(), box.getMinY(), box.getMinZ(),
      box.getMaxX(), box.getMaxY(), box.getMaxZ()
    );
    if (isOriginBox(box, posX, posY, posZ)) {
      translated.makeOriginBox();
      return translated.contextualized(posX, posY, posZ);
    }
    return translated;
  }

  private boolean isOriginBox(org.bukkit.util.BoundingBox box, int posX, int posY, int posZ) {
    if (posX == 0 && posY == 0 && posZ == 0) {
      return true;
    }
    return box.getMinX() >= -0.00001 && box.getMaxX() <= 1.00001
      && box.getMinY() >= -0.00001 && box.getMaxY() <= 1.00001
      && box.getMinZ() >= -0.00001 && box.getMaxZ() <= 1.00001;
  }

  private static Method method(Class<?> owner, String name, Class<?>... parameterTypes) {
    try {
      return owner.getMethod(name, parameterTypes);
    } catch (NoSuchMethodException exception) {
      return null;
    }
  }
}
