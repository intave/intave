package de.jpx3.intave.world.blockshape.boxresolver.drill;

import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.world.blockshape.boxresolver.ResolverPipeline;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import net.minecraft.server.v1_13_R2.*;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_13_R2.util.CraftMagicNumbers;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@PatchyAutoTranslation
public final class v13BoundingBoxDrill implements ResolverPipeline {
  @Override
  @PatchyAutoTranslation
  public List<WrappedAxisAlignedBB> resolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    WorldServer handle = ((CraftWorld) world).getHandle();
    BlockPosition blockPosition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = CraftMagicNumbers.getBlock(type, (byte) blockState);
    if (blockData == null) {
      return Collections.emptyList();
    }
    VoxelShape collisionShape = blockData.getCollisionShape(handle, blockPosition);
    List<AxisAlignedBB> nativeBoxes = collisionShape.d();
    return translate(nativeBoxes, posX, posY, posZ);
  }

  private List<WrappedAxisAlignedBB> translate(List<?> bbs, int posX, int posY, int posZ) {
    if (bbs.isEmpty()) {
      return Collections.emptyList();
    }
    return bbs.stream().map(bb -> WrappedAxisAlignedBB.fromNative(bb).offset(posX, posY, posZ)).collect(Collectors.toList());
  }
}