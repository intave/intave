package de.jpx3.intave.block.shape.resolve.patch;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_13;

final class HopperPatch extends BlockShapePatch {
  private static final float WALL_WIDTH = 2.0f;
  private static final float BOX_HEIGHT = 5.0f;
  private static final BlockShape SHAPE_A = BoundingBox.originFromX16(0, 16 - BOX_HEIGHT, 0, WALL_WIDTH, 16, 16);
  private static final BlockShape SHAPE_B = BoundingBox.originFromX16(WALL_WIDTH, 16 - BOX_HEIGHT, 0, 16, 16, WALL_WIDTH);
  private static final BlockShape SHAPE_C = BoundingBox.originFromX16(WALL_WIDTH, 16 - BOX_HEIGHT, 16.0 - WALL_WIDTH, 16, 16, 16);
  private static final BlockShape SHAPE_D = BoundingBox.originFromX16(16.0 - WALL_WIDTH, 16 - BOX_HEIGHT, WALL_WIDTH, 16, 16, 16 - WALL_WIDTH);
  private static final BlockShape SHAPE_WALLS = BlockShapes.merge(SHAPE_A, SHAPE_B, SHAPE_C, SHAPE_D);
  private static final BlockShape SHAPE_CLOSURE = BoundingBox.originFromX16(0, 10.0, 0, 16, 16 - BOX_HEIGHT, 16);
  private static final BlockShape SHAPE_BASE = BoundingBox.originFromX16(4, 4, 4, 12, 10.0, 12);
  private static final BlockShape MAIN_SHAPE = BlockShapes.merge(SHAPE_WALLS, SHAPE_CLOSURE, SHAPE_BASE);

  private static final BlockShape SHAPE_DOWN = BlockShapes.merge(MAIN_SHAPE, BoundingBox.originFromX16(6.0D, 0.0D, 6.0D, 10.0D, 4.0D, 10.0D));
  private static final BlockShape SHAPE_NORTH = BlockShapes.merge(MAIN_SHAPE, BoundingBox.originFromX16(12.0D, 4.0D, 6.0D, 16.0D, 8.0D, 10.0D));
  private static final BlockShape SHAPE_EAST = BlockShapes.merge(MAIN_SHAPE, BoundingBox.originFromX16(6.0D, 4.0D, 0.0D, 10.0D, 8.0D, 4.0D));
  private static final BlockShape SHAPE_SOUTH = BlockShapes.merge(MAIN_SHAPE, BoundingBox.originFromX16(6.0D, 4.0D, 12.0D, 10.0D, 8.0D, 16.0D));
  private static final BlockShape SHAPE_WEST = BlockShapes.merge(MAIN_SHAPE, BoundingBox.originFromX16(0.0D, 4.0D, 6.0D, 4.0D, 8.0D, 10.0D));

  private static final BlockShape LEGACY_SHAPE = BlockShapes.merge(SHAPE_WALLS, BoundingBox.originFromX16(0, 0, 0, 16, 10.0f, 16));

  @Override
  protected BlockShape collisionPatch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, BlockShape originalShape) {
    User user = UserRepository.userOf(player);
    if (user.protocolVersion() >= VER_1_13) {
      if (MinecraftVersion.AQUATIC_UPDATE.atOrAbove()) {
        return originalShape;
      } else {
        BlockVariant variant = BlockVariantRegister.variantOf(Material.HOPPER, blockState);
        Direction direction = variant.enumProperty(Direction.class, "facing");
        BlockShape shape;
        switch (direction) {
          case DOWN:
            shape = SHAPE_DOWN;
            break;
          case NORTH:
            shape = SHAPE_NORTH;
            break;
          case SOUTH:
            shape = SHAPE_SOUTH;
            break;
          case WEST:
            shape = SHAPE_WEST;
            break;
          case EAST:
            shape = SHAPE_EAST;
            break;
          case UP:
            throw new IllegalStateException("Hopper cannot be facing up");
          default:
            shape = MAIN_SHAPE;
            break;
        }
        return shape;
      }
    } else {
      if (MinecraftVersion.AQUATIC_UPDATE.atOrAbove()) {
        return LEGACY_SHAPE;
      } else {
        return originalShape;
      }
    }
  }

  @Override
  public boolean appliesTo(Material material) {
    return material.name().contains("HOPPER");
  }
}