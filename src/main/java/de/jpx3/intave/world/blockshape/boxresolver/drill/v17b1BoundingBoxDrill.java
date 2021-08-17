package de.jpx3.intave.world.blockshape.boxresolver.drill;

import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.world.blockaccess.RuntimeBlockDataIndexer;
import de.jpx3.intave.world.blockshape.boxresolver.ResolverPipeline;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import net.minecraft.core.BlockPosition;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@PatchyAutoTranslation
public final class v17b1BoundingBoxDrill implements ResolverPipeline {
  @Override
  @PatchyAutoTranslation
  public List<WrappedAxisAlignedBB> resolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    WorldServer handle = ((CraftWorld) world).getHandle();
    BlockPosition blockPosition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = (IBlockData) RuntimeBlockDataIndexer.modernStateFromIndex(type, blockState);
    if (blockData == null) {
      return Collections.emptyList();
    }
    VoxelShape collisionShape = blockData.getCollisionShape(handle, blockPosition);
    List<AxisAlignedBB> nativeBoxes = collisionShape.toList();
    return translate(nativeBoxes, posX, posY, posZ);
  }

  private List<WrappedAxisAlignedBB> translate(List<?> bbs, int posX, int posY, int posZ) {
    if (bbs.isEmpty()) {
      return Collections.emptyList();
    }
    return bbs.stream().map(bb -> WrappedAxisAlignedBB.fromNative(bb).offset(posX, posY, posZ)).collect(Collectors.toList());
  }
}