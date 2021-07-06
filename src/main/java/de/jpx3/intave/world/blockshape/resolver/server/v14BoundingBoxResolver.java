package de.jpx3.intave.world.blockshape.resolver.server;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.blockaccess.RuntimeBlockDataIndexer;
import de.jpx3.intave.world.blockshape.resolver.BoundingBoxResolvePipeline;
import net.minecraft.server.v1_14_R1.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_14_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_14_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@PatchyAutoTranslation
public final class v14BoundingBoxResolver implements BoundingBoxResolvePipeline {
  @Override
  @PatchyAutoTranslation
  public List<WrappedAxisAlignedBB> nativeResolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    Location location = new Location(world, posX, posY, posZ);
    org.bukkit.block.Block block = BukkitBlockAccess.blockAccess(location);
    return customResolve(world, player, type, BlockDataAccess.dataAccess(block), posX, posY, posZ);
  }

  private final static boolean NEW_MATERIAL_PROCESSING = MinecraftVersions.VER1_14_0.atOrAbove();

  @Override
  @PatchyAutoTranslation
  public List<WrappedAxisAlignedBB> customResolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    WorldServer handle = ((CraftWorld) world).getHandle();
    BlockPosition blockPosition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData;
    if (NEW_MATERIAL_PROCESSING) {
      blockData = (IBlockData) RuntimeBlockDataIndexer.modernStateFromIndex(type, blockState);
    } else {
      blockData = CraftMagicNumbers.getBlock(type, (byte) blockState);
    }
    if (blockData == null) {
      return Collections.emptyList();
    }
    VoxelShape collisionShape = blockData.getCollisionShape(handle, blockPosition);
    List<AxisAlignedBB> nativeBoxes = collisionShape.d();
    if (nativeBoxes.isEmpty()) {
      return Collections.emptyList();
    }
    return translate(nativeBoxes, posX, posY, posZ);
  }

  private List<WrappedAxisAlignedBB> translate(List<?> bbs, int posX, int posY, int posZ) {
    if (bbs.isEmpty()) {
      return Collections.emptyList();
    }
    return bbs.stream().map(bb -> WrappedAxisAlignedBB.fromNative(bb).offset(posX, posY, posZ)).collect(Collectors.toList());
  }
}