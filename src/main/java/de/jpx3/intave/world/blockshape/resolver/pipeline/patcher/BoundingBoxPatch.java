package de.jpx3.intave.world.blockshape.resolver.pipeline.patcher;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public abstract class BoundingBoxPatch {
  private final Material[] material;

  protected BoundingBoxPatch(Material... materials) {
    this.material = materials;
  }

  protected List<WrappedAxisAlignedBB> patch(World world, Player player, Block block, List<WrappedAxisAlignedBB> bbs) {
    return bbs;
  }

  protected List<WrappedAxisAlignedBB> patch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<WrappedAxisAlignedBB> bbs) {
    return bbs;
  }

  protected boolean requireRepose() {
    return false;
  }

  public boolean appliesTo(Material material) {
    return Arrays.stream(this.material).anyMatch(matcher -> matcher == material);
  }
}