package de.jpx3.intave.world.collision.patches;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2021
 */

public abstract class BoundingBoxPatch {
  private final Material material;

  protected BoundingBoxPatch(Material material) {
    this.material = material;
  }

  public List<WrappedAxisAlignedBB> patch(World world, Player player, Block block, List<WrappedAxisAlignedBB> bbs) {
    return bbs;
  }

  public List<WrappedAxisAlignedBB> patch(World world, Player player, Material type, int blockState, List<WrappedAxisAlignedBB> bbs) {
    return bbs;
  }

  public Material material() {
    return material;
  }
}
