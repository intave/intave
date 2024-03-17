package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class LadderBlockPatch extends BlockShapePatch {
  private static final boolean MODERN_PATCH_REDUNDANT = MinecraftVersions.VER1_13_0.atOrAbove();

  public LadderBlockPatch() {
    super(Material.LADDER);
  }

  @Override
  public BlockShape collisionPatch(World world, Player player, int posX, int posY, int posZ, Material type, int variantIndex, BlockShape originalShape) {
    User user = UserRepository.userOf(player);
    BlockVariant variant = BlockVariantRegister.variantOf(type, variantIndex);
    Direction direction = variant.enumProperty(Direction.class, "facing");
    boolean modern = user.meta().protocol().combatUpdate();
    if (modern) {
      return MODERN_PATCH_REDUNDANT ? originalShape : modernPath(direction);
    } else {
      return legacyPatch(direction);
    }
  }

  private BlockShape modernPath(Direction direction) {
    switch (direction) {
      case NORTH:
        return BoundingBox.originFrom(0.0f, 0.0f, 0.8125f, 1.0f, 1.0f, 1.0f);
      case SOUTH:
        return BoundingBox.originFrom(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.1875f);
      case WEST:
        return BoundingBox.originFrom(0.8125f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
      case EAST:
        return BoundingBox.originFrom(0.0f, 0.0f, 0.0f, 0.1875f, 1.0f, 1.0f);
    }
    return BlockShapes.emptyShape();
  }

  private BlockShape legacyPatch(Direction direction) {
    switch (direction) {
      case NORTH:
        return BoundingBox.originFrom(0.0F, 0.0F, 0.875f, 1.0F, 1.0F, 1.0F);
      case SOUTH:
        return BoundingBox.originFrom(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.125F);
      case WEST:
        return BoundingBox.originFrom(0.875f, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
      case EAST: {
        return BoundingBox.originFrom(0.0F, 0.0F, 0.0F, 0.125F, 1.0F, 1.0F);
      }
    }
    return BlockShapes.emptyShape();
  }
}