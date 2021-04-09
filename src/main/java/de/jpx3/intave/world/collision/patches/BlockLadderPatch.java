package de.jpx3.intave.world.collision.patches;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedEnumDirection;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.collision.BoundingBoxBuilder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

public final class BlockLadderPatch extends BoundingBoxPatch {
  protected BlockLadderPatch() {
    super(Material.LADDER);
  }

  @Override
  public List<WrappedAxisAlignedBB> patch(World world, Player player, Block block, List<WrappedAxisAlignedBB> bbs) {
    return patch(world, player, block.getType(), block.getData(), bbs);
  }

  @Override
  public List<WrappedAxisAlignedBB> patch(World world, Player player, Material type, int blockState, List<WrappedAxisAlignedBB> bbs) {
    User user = UserRepository.userOf(player);
    BoundingBoxBuilder builder = BoundingBoxBuilder.create();
    WrappedEnumDirection direction = WrappedEnumDirection.getFront(blockState);
    if (user.meta().clientData().applyNewEntityCollisions()) {
      emulateNew(builder, direction);
    } else {
      emulateLegacy(builder, direction);
    }
    return builder.applyAndResolve();
  }

  private void emulateNew(BoundingBoxBuilder builder, WrappedEnumDirection direction) {
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
  }

  private void emulateLegacy(BoundingBoxBuilder builder, WrappedEnumDirection direction) {
    float var4 = 0.125F;
    switch (direction) {
      case NORTH:
        builder.shape(0.0F, 0.0F, 1.0F - var4, 1.0F, 1.0F, 1.0F);
        break;
      case SOUTH:
        builder.shape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, var4);
        break;
      case WEST:
        builder.shape(1.0F - var4, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        break;
      case EAST: {
        builder.shape(0.0F, 0.0F, 0.0F, var4, 1.0F, 1.0F);
        break;
      }
    }
  }
}