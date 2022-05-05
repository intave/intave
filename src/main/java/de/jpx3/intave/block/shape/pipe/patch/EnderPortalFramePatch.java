package de.jpx3.intave.block.shape.pipe.patch;

import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.block.variant.BlockVariantAccess;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class EnderPortalFramePatch extends BoundingBoxPatch {
  private final BoundingBox baseShape = BoundingBox.originFromX16(0, 0, 0, 16, 13, 16);
  private final BoundingBox eye8 = BoundingBox.originFromX16(5, 13, 5, 11, 16, 11);
  private final BoundingBox eye13 = BoundingBox.originFromX16(4, 13, 4, 12, 16, 12);

  @Override
  protected List<BoundingBox> patch(World world, Player player, Block block, List<BoundingBox> bbs) {
    return patch(world, player, block.getX(), block.getY(), block.getZ(), BlockTypeAccess.typeAccess(block, player), BlockVariantAccess.variantAccess(block), bbs);
  }

  @Override
  protected List<BoundingBox> patch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<BoundingBox> bbs) {
    List<BoundingBox> boundingBoxes = new ArrayList<>();
    boundingBoxes.add(baseShape);
    if ((blockState & 4) != 0) {
      User user = UserRepository.userOf(player);
      if (user.meta().protocol().waterUpdate()) {
        boundingBoxes.add(eye13);
      } else {
        boundingBoxes.add(eye8);
      }
    }
    return boundingBoxes;
  }

  @Override
  public boolean appliesTo(Material material) {
    return material.name().endsWith("PORTAL_FRAME");
  }
}
