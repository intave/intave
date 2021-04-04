package de.jpx3.intave.world.collision.patches;

import de.jpx3.intave.reflect.ReflectiveMaterialAccess;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.collision.BoundingBoxBuilder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_COMBAT_UPDATE;

public final class BlockLilyPadPatch extends BoundingBoxPatch {
  protected BlockLilyPadPatch() {
    super(ReflectiveMaterialAccess.materialById(111));
  }

  @Override
  public List<WrappedAxisAlignedBB> patch(World world, Player player, Block block, List<WrappedAxisAlignedBB> bbs) {
    return patch(world, player, block.getType(), block.getData(), bbs);
  }

  @Override
  public List<WrappedAxisAlignedBB> patch(World world, Player player, Material type, int blockState, List<WrappedAxisAlignedBB> bbs) {
    User user = UserRepository.userOf(player);
    BoundingBoxBuilder builder = BoundingBoxBuilder.create();
    if (user.meta().clientData().protocolVersion() >= PROTOCOL_VERSION_COMBAT_UPDATE) {
      builder.shape(0.0625f, 0.0f, 0.0625f, 0.9375f, 0.09375f, 0.9375f);
    } else {
      float f = 0.5F;
      float f1 = 0.015625F;
      builder.shape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, f1, 0.5F + f);
    }
    builder.apply();
    return builder.resolve();
  }
}