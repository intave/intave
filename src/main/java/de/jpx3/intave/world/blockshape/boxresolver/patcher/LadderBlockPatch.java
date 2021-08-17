package de.jpx3.intave.world.blockshape.boxresolver.patcher;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BlockTypeAccess;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.wrapper.WrappedEnumDirection;
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
  public List<WrappedAxisAlignedBB> patch(World world, Player player, Block block, List<WrappedAxisAlignedBB> bbs) {
    return patch(world, player, block.getX(), block.getY(), block.getZ(), BlockTypeAccess.typeAccess(block, player), BlockDataAccess.dataAccess(block), bbs);
  }

  @Override
  public List<WrappedAxisAlignedBB> patch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<WrappedAxisAlignedBB> bbs) {
    User user = UserRepository.userOf(player);
    WrappedEnumDirection direction = WrappedEnumDirection.getFront(blockState);
    boolean modern = user.meta().protocol().combatUpdate();
    if (modern) {
      return MODERN_PATCH_REDUNDANT ? bbs : modernPath(direction);
    } else {
      return legacyPatch(direction);
    }
  }

  private List<WrappedAxisAlignedBB> modernPath(WrappedEnumDirection direction) {
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

  private List<WrappedAxisAlignedBB> legacyPatch(WrappedEnumDirection direction) {
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