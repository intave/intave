package de.jpx3.intave.world.blockshape.resolver.pipeline.patcher;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedEnumDirection;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BlockTypeAccess;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

final class AnvilBlockPatch extends BoundingBoxPatch {
  public AnvilBlockPatch() {
    super(Material.ANVIL);
  }

  @Override
  public List<WrappedAxisAlignedBB> patch(World world, Player player, Block block, List<WrappedAxisAlignedBB> bbs) {
    return patch(world, player, block.getX(), block.getY(), block.getZ(), BlockTypeAccess.typeAccess(block, player), BlockDataAccess.dataAccess(block), bbs);
  }

  @Override
  public List<WrappedAxisAlignedBB> patch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<WrappedAxisAlignedBB> bbs) {
    User user = UserRepository.userOf(player);
    WrappedEnumDirection.Axis axis = axisOf(blockState);
    boolean legacy = user.meta().protocolData().protocolVersion() < ProtocolMetadata.VER_1_13;
    if (legacy) {
      BoundingBoxBuilder boundingBoxBuilder = BoundingBoxBuilder.create();
      if (axis == WrappedEnumDirection.Axis.X) {
        boundingBoxBuilder.shape(0.0F, 0.0F, 0.125F, 1.0F, 1.0F, 0.875F);
      } else {
        boundingBoxBuilder.shape(0.125F, 0.0F, 0.0F, 0.875F, 1.0F, 1.0F);
      }
      return boundingBoxBuilder.applyAndResolve();
    }
    ApplyOnShapeBoundingBoxBuilder boundingBoxBuilder = ApplyOnShapeBoundingBoxBuilder.create();
    if (axis == WrappedEnumDirection.Axis.X) {
      boundingBoxBuilder.shapeX16(2.0D, 0.0D, 2.0D, 14.0D, 4.0D, 14.0D);
      boundingBoxBuilder.shapeX16(3.0D, 4.0D, 4.0D, 13.0D, 5.0D, 12.0D);
      boundingBoxBuilder.shapeX16(4.0D, 5.0D, 6.0D, 12.0D, 10.0D, 10.0D);
      boundingBoxBuilder.shapeX16(0.0D, 10.0D, 3.0D, 16.0D, 16.0D, 13.0D);
    } else {
      boundingBoxBuilder.shapeX16(2.0D, 0.0D, 2.0D, 14.0D, 4.0D, 14.0D);
      boundingBoxBuilder.shapeX16(4.0D, 4.0D, 3.0D, 12.0D, 5.0D, 13.0D);
      boundingBoxBuilder.shapeX16(6.0D, 5.0D, 4.0D, 10.0D, 10.0D, 12.0D);
      boundingBoxBuilder.shapeX16(3.0D, 10.0D, 0.0D, 13.0D, 16.0D, 16.0D);
    }
    return boundingBoxBuilder.resolve();
  }

  private final static boolean CORRUPTED = MinecraftVersions.VER1_14_0.atOrAbove();

  private WrappedEnumDirection.Axis axisOf(int state) {
    if (CORRUPTED) {
      switch (state) {
        case 1:
          return WrappedEnumDirection.Axis.Z;
        case 2:
          return WrappedEnumDirection.Axis.X;
      }
    }
    return WrappedEnumDirection.getHorizontal(state & 3).getAxis();
  }
}