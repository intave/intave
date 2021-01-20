package de.jpx3.intave.world.collision;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.patchy.annotate.PatchyTranslateParameters;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftChunk;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LegacyBoundingBoxResolver implements BoundingBoxResolver {
  private final static AlwaysCollidingBoundingBox ALWAYS_COLLIDING_BOX = new AlwaysCollidingBoundingBox();

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
      ((CraftWorld) world).getHandle(),
      blockposition,
      blockData,
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
    IBlockData blockData = Block.d.a((typeId << 4) | (blockState & 0xF));
    List<AxisAlignedBB> bbs = new ArrayList<>();
    if(blockData == null) {
      return Collections.emptyList();
    }
    blockData.getBlock().a(
      ((CraftWorld) world).getHandle(),
      blockposition,
      blockData,
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

  static {
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), "de.jpx3.intave.world.collision.LegacyBoundingBoxResolver$AlwaysCollidingBoundingBox");
  }

  @PatchyAutoTranslation
  public static class AlwaysCollidingBoundingBox extends AxisAlignedBB {
    @PatchyAutoTranslation
    public AlwaysCollidingBoundingBox() {
      super(0,0,0,1,1,1);
    }

    @Override
    @PatchyAutoTranslation
    @PatchyTranslateParameters
    public boolean b(AxisAlignedBB axisAlignedBB) {
      return true;
    }
  }
}
