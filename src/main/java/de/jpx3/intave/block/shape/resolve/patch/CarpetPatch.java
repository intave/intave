package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

final class CarpetPatch extends BlockShapePatch {
  @Override
  protected BlockShape collisionPatch(World world, User user, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
//    System.out.println("CarpetPatch.patch at " + posX + " " + posY + " " + posZ + " with state " + blockState);
    return /*user.protocolVersion() <= 5 ?
      Collections.emptyList() : */shape;
  }

  @Override
  public boolean appliesTo(Material material) {
    return material.name().contains("CARPET");
  }
}
