package de.jpx3.intave.block.shape.resolve.patch;

import com.google.common.collect.Lists;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;

final class CauldronBlockPatch extends BlockShapePatch {
  private static final BlockShape shape8;
  private static final BlockShape shape13;

  static {
    float wallWidth = 2f /*/ 16f*/;
    shape8 = BlockShapes.merge(Lists.newArrayList(
      BoundingBox.originFromX16(0.0f, 0.0F, 0.0F, 16.0F, 5.0f, 16.0F),
      BoundingBox.originFromX16(0.0F, 0.0F, 0.0F, wallWidth, 16.0F, 16.0F),
      BoundingBox.originFromX16(0.0F, 0.0F, 0.0F, 16.0F, 16.0F, wallWidth),
      BoundingBox.originFromX16(16.0F - wallWidth, 0.0F, 0.0F, 16.0F, 16.0F, 16.0F),
      BoundingBox.originFromX16(0.0F, 0.0F, 16.0F - wallWidth, 16.0F, 16.0F, 16.0F)
    ));
    shape13 = BlockShapes.merge(Lists.newArrayList(
      BoundingBox.originFromX16(0.0F, 0.0F, 0.0F, 16.0F, 4.0f, 16.0F),
      BoundingBox.originFromX16(0.0F, 0.0F, 0.0F, wallWidth, 16.0F, 16.0F),
      BoundingBox.originFromX16(0.0F, 0.0F, 0.0F, 16.0F, 16.0F, wallWidth),
      BoundingBox.originFromX16(16.0F - wallWidth, 0.0F, 0.0F, 16.0F, 16.0F, 16.0F),
      BoundingBox.originFromX16(0.0F, 0.0F, 16.0F - wallWidth, 16.0F, 16.0F, 16.0F)
    ));
  }

  private static final boolean SERVER_IS_1_13 = MinecraftVersions.VER1_13_0.atOrAbove();

  @Override
  protected BlockShape collisionPatch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    User user = UserRepository.userOf(player);
    if (user.meta().protocol().waterUpdate()) {
      if (SERVER_IS_1_13) {
        return shape;
      } else {
        return shape13;
      }
    } else {
      if (SERVER_IS_1_13) {
        return shape8;
      } else {
        return shape;
      }
    }
  }

  @Override
  public boolean appliesTo(Material material) {
    return material.name().toLowerCase(Locale.ROOT).contains("cauldron");
  }
}
