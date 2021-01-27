package de.jpx3.intave.world.collision.resolver;

import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.collision.BoundingBoxResolver;
import de.jpx3.intave.world.collision.resolver.ac.v1_9AlwaysCollidingBoundingBox;
import net.minecraft.server.v1_9_R2.*;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_9_R2.CraftChunk;
import org.bukkit.craftbukkit.v1_9_R2.CraftWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PatchyAutoTranslation
public final class v1_9BoundingBoxResolver implements BoundingBoxResolver {
  private final static v1_9AlwaysCollidingBoundingBox ALWAYS_COLLIDING_BOX = new v1_9AlwaysCollidingBoundingBox();

  @Override
  @PatchyAutoTranslation
  public List<WrappedAxisAlignedBB> resolve(World world, int posX, int posY, int posZ) {
    Chunk handle = ((CraftChunk) world.getChunkAt(posX >> 4, posZ >> 4)).getHandle();
    BlockPosition blockposition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = handle.getBlockData(blockposition);
    if(blockData == null) {
      return Collections.emptyList();
    }
    List<AxisAlignedBB> bbs = new ArrayList<>();

    blockData.getBlock().a(
      blockData,
      ((CraftWorld) world).getHandle(),
      blockposition,
      ALWAYS_COLLIDING_BOX,
      bbs,
      null
    );
    return translate(bbs);
  }

  @Override
  @PatchyAutoTranslation
  public List<WrappedAxisAlignedBB> resolve(World world, int posX, int posY, int posZ, int typeId, int blockState) {
    BlockPosition blockposition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = Block.getByCombinedId(typeId | blockState << 12 & 0xF);
    List<AxisAlignedBB> bbs = new ArrayList<>();
    if(blockData == null) {
      return Collections.emptyList();
    }
    blockData.getBlock().a(
      blockData,
      ((CraftWorld) world).getHandle(),
      blockposition,
      ALWAYS_COLLIDING_BOX,
      bbs,
      null
    );
    return translate(bbs);
  }

  private List<WrappedAxisAlignedBB> translate(List<?> bbs) {
    if(bbs.isEmpty()) {
      return Collections.emptyList();
    }
    List<WrappedAxisAlignedBB> list = new ArrayList<>();
    for (Object bb : bbs) {
      list.add(WrappedAxisAlignedBB.fromClass(bb));
    }
    return list;
  }
}
