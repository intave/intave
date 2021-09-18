package de.jpx3.intave.block.shape.pipe.patch;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.BlockVariantAccess;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.EnumDirection;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

final class LadderBlockPatch extends BoundingBoxPatch {
  private static final boolean MODERN_PATCH_REDUNDANT = MinecraftVersions.VER1_13_0.atOrAbove();

  public LadderBlockPatch() {
    super(Material.LADDER);
  }

  @Override
  public List<BoundingBox> patch(World world, Player player, Block block, List<BoundingBox> bbs) {
    return patch(world, player, block.getX(), block.getY(), block.getZ(), BlockTypeAccess.typeAccess(block, player), BlockVariantAccess.variantAccess(block), bbs);
  }

  @Override
  public List<BoundingBox> patch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<BoundingBox> bbs) {
    User user = UserRepository.userOf(player);
    EnumDirection direction = EnumDirection.getFront(blockState);
    boolean modern = user.meta().protocol().combatUpdate();
    if (modern) {
      return MODERN_PATCH_REDUNDANT ? bbs : modernPath(direction);
    } else {
      return legacyPatch(direction);
    }
  }

  private List<BoundingBox> modernPath(EnumDirection direction) {
    BoundingBoxBuilder builder = BoundingBoxBuilder.create();
    switch (direction) {
      case NORTH:
        builder.shape(0.0f, 0.0f, 0.8125f, 1.0f, 1.0f, 1.0f);
        break;
      case SOUTH:
        builder.shape(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.1875f);
        break;
      case WEST:
        builder.shape(0.8125f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
        break;
      case EAST:
        builder.shape(0.0f, 0.0f, 0.0f, 0.1875f, 1.0f, 1.0f);
        break;
    }
    return builder.applyAndResolve();
  }

  private List<BoundingBox> legacyPatch(EnumDirection direction) {
    BoundingBoxBuilder builder = BoundingBoxBuilder.create();
    switch (direction) {
      case NORTH:
        builder.shape(0.0F, 0.0F, 0.875f, 1.0F, 1.0F, 1.0F);
        break;
      case SOUTH:
        builder.shape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.125F);
        break;
      case WEST:
        builder.shape(0.875f, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        break;
      case EAST: {
        builder.shape(0.0F, 0.0F, 0.0F, 0.125F, 1.0F, 1.0F);
        break;
      }
    }
    return builder.applyAndResolve();
  }
}