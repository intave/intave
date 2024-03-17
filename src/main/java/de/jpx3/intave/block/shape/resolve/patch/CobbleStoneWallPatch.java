package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.resolve.patch.cobblewall.WallConnectResolver;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.klass.rewrite.PatchyLoadingInjector;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.share.Direction.*;

class CobbleStoneWallPatch extends BlockShapePatch {
  private static final boolean VILLAGE_UPDATE = MinecraftVersions.VER1_14_0.atOrAbove();
  private static final boolean AQUATIC_UPDATE = MinecraftVersions.VER1_13_0.atOrAbove();
  private static final boolean COLOR_UPDATE = MinecraftVersions.VER1_12_0.atOrAbove();
  private static final boolean COMBAT_UPDATE = MinecraftVersions.VER1_9_0.atOrAbove();
  private static WallConnectResolver connectResolver;
  private static final BoundingBox[] BOUNDING_BOXES =
      new BoundingBox[] {
        BoundingBox.fromBounds(0.25D, 0.0D, 0.25D, 0.75D, 1.0D, 0.75D),
        BoundingBox.fromBounds(0.25D, 0.0D, 0.25D, 0.75D, 1.0D, 1.0D),
        BoundingBox.fromBounds(0.0D, 0.0D, 0.25D, 0.75D, 1.0D, 0.75D),
        BoundingBox.fromBounds(0.0D, 0.0D, 0.25D, 0.75D, 1.0D, 1.0D),
        BoundingBox.fromBounds(0.25D, 0.0D, 0.0D, 0.75D, 1.0D, 0.75D),
        BoundingBox.fromBounds(0.3125D, 0.0D, 0.0D, 0.6875D, 0.875D, 1.0D),
        BoundingBox.fromBounds(0.0D, 0.0D, 0.0D, 0.75D, 1.0D, 0.75D),
        BoundingBox.fromBounds(0.0D, 0.0D, 0.0D, 0.75D, 1.0D, 1.0D),
        BoundingBox.fromBounds(0.25D, 0.0D, 0.25D, 1.0D, 1.0D, 0.75D),
        BoundingBox.fromBounds(0.25D, 0.0D, 0.25D, 1.0D, 1.0D, 1.0D),
        BoundingBox.fromBounds(0.0D, 0.0D, 0.3125D, 1.0D, 0.875D, 0.6875D),
        BoundingBox.fromBounds(0.0D, 0.0D, 0.25D, 1.0D, 1.0D, 1.0D),
        BoundingBox.fromBounds(0.25D, 0.0D, 0.0D, 1.0D, 1.0D, 0.75D),
        BoundingBox.fromBounds(0.25D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D),
        BoundingBox.fromBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.75D),
        BoundingBox.fromBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)
      };

  private static final Direction[] SIDE_DIRECTIONS = new Direction[] {SOUTH, WEST, NORTH, EAST};

  static {
    // Only apply on 1.9.0-1.13.2
    if (!COMBAT_UPDATE || VILLAGE_UPDATE) {
      connectResolver = null;
    } else {
      ClassLoader classLoader = IntavePlugin.class.getClassLoader();
      // ! Only full class paths (without concatenations) are supported, CHANGE THIS
      String className = "";
      if (AQUATIC_UPDATE) {
        className = "de.jpx3.intave.block.shape.resolve.patch.cobblewall.v13WallConnectResolver";
      } else if (COLOR_UPDATE) {
        className = "de.jpx3.intave.block.shape.resolve.patch.cobblewall.v12WallConnectResolver";
      } else {
        className = "de.jpx3.intave.block.shape.resolve.patch.cobblewall.v9WallConnectResolver";
      }
      Class<WallConnectResolver> resolverClass =
          PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, className);
      if (resolverClass != null) {
        try {
          connectResolver = resolverClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
          e.printStackTrace();
        }
      } else {
        connectResolver = null;
      }
    }
  }

  public CobbleStoneWallPatch() {
    super(BlockTypeAccess.COBBLESTONE_WALL);
  }

  @Override
  protected BlockShape collisionPatch(
      World world,
      Player player,
      int posX,
      int posY,
      int posZ,
      Material type,
      int blockState,
      BlockShape shape) {
    // Only apply on 1.9.0-1.13.2
    if (connectResolver == null) {
      return shape;
    }
    List<Direction> connected = surroundedFacings(world, posX, posY, posZ);
    BoundingBox selectedBox = BOUNDING_BOXES[connectionIndex(connected)];
    // Check thick types
    return selectedBox.grow(0, 1.5 - selectedBox.maxY, 0).offset(posX, posY, posZ);
  }

  @Override
  public boolean appliesTo(Material material) {
    return BlockTypeAccess.COBBLESTONE_WALL == material;
  }

  private List<Direction> surroundedFacings(World world, int posX, int posY, int posZ) {
    List<Direction> connected = new ArrayList<>();
    Direction direction = UP;
    int blockX = (int) (posX + direction.directionVector().xCoord);
    int blockY = (int) (posY + direction.directionVector().yCoord);
    int blockZ = (int) (posZ + direction.directionVector().zCoord);
    Block block = VolatileBlockAccess.blockAccess(world, blockX, blockY, blockZ);
    Material blockType = block.getType();
    if (blockType != Material.AIR) {
      connected.add(direction);
    }
    for (Direction sideDirection : SIDE_DIRECTIONS) {
      if (connectResolver.canConnectTo(
          world,
          new BlockPosition(posX, posY, posZ).add(sideDirection.directionVector()),
          sideDirection)) {
        connected.add(sideDirection);
      }
    }
    return connected;
  }

  private static int connectionIndex(List<Direction> state) {
    int index = 0;
    if (state.contains(NORTH)) {
      index |= 1 << NORTH.getHorizontalIndex();
    }
    if (state.contains(EAST)) {
      index |= 1 << EAST.getHorizontalIndex();
    }
    if (state.contains(SOUTH)) {
      index |= 1 << SOUTH.getHorizontalIndex();
    }
    if (state.contains(WEST)) {
      index |= 1 << WEST.getHorizontalIndex();
    }
    return index;
  }
}
