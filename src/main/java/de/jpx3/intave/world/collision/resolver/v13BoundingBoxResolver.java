package de.jpx3.intave.world.collision.resolver;

import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.collision.BoundingBoxResolver;
import net.minecraft.server.v1_13_R2.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_13_R2.util.CraftMagicNumbers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@PatchyAutoTranslation
public final class v13BoundingBoxResolver implements BoundingBoxResolver {
  @Override
  @PatchyAutoTranslation
  public List<WrappedAxisAlignedBB> resolve(World world, Material advanceType, int posX, int posY, int posZ) {
    Location location = new Location(world, posX, posY, posZ);
    org.bukkit.block.Block block = BukkitBlockAccess.blockAccess(location);
    return resolve(world, posX, posY, posZ, advanceType, block.getData());
  }

  @Override
  @PatchyAutoTranslation
  public List<WrappedAxisAlignedBB> resolve(World world, int posX, int posY, int posZ, Material type, int blockState) {
    WorldServer handle = ((CraftWorld) world).getHandle();
    BlockPosition blockPosition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = CraftMagicNumbers.getBlock(type, (byte) blockState);
    if(blockData == null) {
      return Collections.emptyList();
    }
    VoxelShape collisionShape = blockData.getCollisionShape(handle, blockPosition);
    List<AxisAlignedBB> nativeBoxes = collisionShape.d();
    if(nativeBoxes.isEmpty()) {
      return Collections.emptyList();
    }
    return translate(nativeBoxes, posX, posY, posZ);
  }

  private List<WrappedAxisAlignedBB> translate(List<?> bbs, int posX, int posY, int posZ) {
    if(bbs.isEmpty()) {
      return Collections.emptyList();
    }
    return bbs.stream().map(bb -> WrappedAxisAlignedBB.fromNative(bb).offset(posX, posY, posZ)).collect(Collectors.toList());
  }
}