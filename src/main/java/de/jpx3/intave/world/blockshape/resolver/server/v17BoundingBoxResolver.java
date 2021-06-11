package de.jpx3.intave.world.blockshape.resolver.server;

import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.blockshape.resolver.BoundingBoxResolvePipeline;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@PatchyAutoTranslation
public final class v17BoundingBoxResolver implements BoundingBoxResolvePipeline {
  @Override
  @PatchyAutoTranslation
  public List<WrappedAxisAlignedBB> nativeResolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
//    Location location = new Location(world, posX, posY, posZ);
//    org.bukkit.block.Block block = BukkitBlockAccess.blockAccess(location);
//    return customResolve(world, player, type, BlockDataAccess.dataAccess(block), posX, posY, posZ);
    return new ArrayList<>();
  }

  @Override
  @PatchyAutoTranslation
  public List<WrappedAxisAlignedBB> customResolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
//    WorldServer handle = ((CraftWorld) world).getHandle();
//    BlockPosition blockPosition = new BlockPosition(posX, posY, posZ);
//    IBlockData blockData;
//    if (BlockDataAccess.isLegacy(type)) {
//      blockData = CraftMagicNumbers.getBlock(type, (byte) blockState);
//    } else {
//      blockData = (IBlockData) RuntimeBlockDataIndexer.modernStateFromIndex(type, blockState);
//    }
//    if (blockData == null) {
//      return Collections.emptyList();
//    }
//    VoxelShape collisionShape = blockData.getCollisionShape(handle, blockPosition);
//    List<AxisAlignedBB> nativeBoxes = collisionShape.d();
//    if (nativeBoxes.isEmpty()) {
//      return Collections.emptyList();
//    }
//    return translate(nativeBoxes, posX, posY, posZ);
    return new ArrayList<>();
  }

  private List<WrappedAxisAlignedBB> translate(List<?> bbs, int posX, int posY, int posZ) {
    if (bbs.isEmpty()) {
      return Collections.emptyList();
    }
    return bbs.stream().map(bb -> WrappedAxisAlignedBB.fromNative(bb).offset(posX, posY, posZ)).collect(Collectors.toList());
  }
}