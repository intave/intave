package de.jpx3.intave.world.collision;

import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.state.BlockState;
import de.jpx3.intave.world.state.BlockStateBoolean;
import de.jpx3.intave.world.state.BlockStateInteger;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Collections;
import java.util.List;

public final class ScaffoldingCollisionModifier extends CollisionModifier {
  private final BlockStateInteger blockDistanceState = BlockStateInteger.of("distance", 0, 7);
  private final BlockStateBoolean blockBottomState = BlockStateBoolean.of("bottom");

  private final BlockState blockState = BlockState.builder()
    .with(blockDistanceState)
    .with(blockBottomState)
    .build();

  @Override
  public List<WrappedAxisAlignedBB> modify(User user, WrappedAxisAlignedBB userBox, int posX, int posY, int posZ, List<WrappedAxisAlignedBB> boxes) {
    if (useCustomCollision(user, posY)) {
      double yStart = 14.0 / 16.0;
      double yEnd = 1.0;
      return Collections.singletonList(WrappedAxisAlignedBB.fromBounds(
        posX, posY + yStart, posZ,
        posX + 1, posY + yEnd, posZ + 1
      ));
    } else {
      if (bottomProperty(user.player().getWorld(), posX, posY, posZ) && useCustomCollision(user, posY - 1)) {
        WrappedAxisAlignedBB collisionShapeTwo = WrappedAxisAlignedBB.fromBounds(posX, posY, posZ, posX + 1.0, posY + 2.0 / 16.0, posZ + 1.0);
        return Collections.singletonList(collisionShapeTwo);
      } else {
        return Collections.emptyList();
      }
    }
  }

  private boolean bottomProperty(World world, int posX, int posY, int posZ) {
    Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
    if (block.getY() < 0) {
      return false;
    }
    return blockState.valueOf(block, blockBottomState) && blockState.valueOf(block, blockDistanceState) != 0;
  }

  private boolean useCustomCollision(User user, double blockY) {
    MovementMetadata movementData = user.meta().movement();
    return movementData.positionY >= blockY + 1 - (double) 0.00001f;
  }

  @Override
  public boolean matches(Material material) {
    String name = material.name();
    return name.contains("SCAFFOLDING");
  }
}
