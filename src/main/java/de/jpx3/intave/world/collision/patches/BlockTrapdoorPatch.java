package de.jpx3.intave.world.collision.patches;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.blockaccess.BlockTypeAccess;
import de.jpx3.intave.world.collision.BoundingBoxBuilder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

public final class BlockTrapdoorPatch extends BoundingBoxPatch {
  protected BlockTrapdoorPatch() {
    super(BlockTypeAccess.TRAP_DOOR);
  }

  /*
   makes state-control constrain redundant
   */
  @Override
  public List<WrappedAxisAlignedBB> patch(World world, Player player, Material type, int blockState, List<WrappedAxisAlignedBB> bbs) {
    BoundingBoxBuilder boundingBoxBuilder = BoundingBoxBuilder.create();
    boolean isTop = (blockState & 8) != 0;
    boolean isOpen = (blockState & 4) != 0;

    if(isOpen) {
      switch (blockState & 3) {
        case 0:
          boundingBoxBuilder.shape(0.0F, 0.0F, 0.8125F, 1.0F, 1.0F, 1.0F);
          break;
        case 1:
          boundingBoxBuilder.shape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.1875F);
          break;
        case 2:
          boundingBoxBuilder.shape(0.8125F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
          break;
        case 3:
          boundingBoxBuilder.shape(0.0F, 0.0F, 0.0F, 0.1875F, 1.0F, 1.0F);
          break;
      }
    } else {
      if(isTop) {
        boundingBoxBuilder.shape(0.0F, 0.8125F, 0.0F, 1.0F, 1.0F, 1.0F);
      } else {
        boundingBoxBuilder.shape(0.0F, 0.0F, 0.0F, 1.0F, 0.1875F, 1.0F);
      }
    }
    return boundingBoxBuilder.applyAndResolve();
  }
}
