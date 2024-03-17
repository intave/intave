package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class LilyPadBlockPatch extends BlockShapePatch {
  @Override
  public BlockShape collisionPatch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    User user = UserRepository.userOf(player);
    if (user.meta().protocol().combatUpdate()) {
      return BoundingBox.originFrom(0.0625f, 0.0f, 0.0625f, 0.9375f, 0.09375f, 0.9375f);
    } else {
      float radius = 0.5F;
      float height = 0.015625F;
      return BoundingBox.originFrom(0.5F - radius, 0.0F, 0.5F - radius, 0.5F + radius, height, 0.5F + radius);
    }
  }

  @Override
  public boolean appliesTo(Material material) {
    String name = material.name();
    return name.contains("WATER_LILY");
  }
}