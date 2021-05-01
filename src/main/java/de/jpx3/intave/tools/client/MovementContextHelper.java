package de.jpx3.intave.tools.client;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.collision.Collision;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.material.Attachable;
import org.bukkit.material.Directional;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Openable;

import java.util.List;

public final class MovementContextHelper {
  public static double jumpMotionFor(Player player, float jumpUpwardsMotion) {
    User user = UserRepository.userOf(player);
    UserMetaPotionData potionData = user.meta().potionData();
    if (potionData.potionEffectJumpDuration > 0) {
      int jumpAmplifier = potionData.potionEffectJumpAmplifier();
      jumpUpwardsMotion += (float) ((jumpAmplifier + 1) * 0.1);
    }
    return jumpUpwardsMotion;
  }

  public static float resolveSlipperiness(User user, Location location) {
    Material type = BukkitBlockAccess.cacheAppliedTypeAccess(user, location);
    float blockSlipperiness;
    switch (type) {
      case PACKED_ICE:
      case ICE: {
        blockSlipperiness = 0.98f;
        break;
      }
      case SLIME_BLOCK: {
        blockSlipperiness = 0.8f;
        break;
      }
      default: {
        blockSlipperiness = 0.6f;
      }
    }
    return blockSlipperiness * 0.91f;
  }

  public static float resolveFriction(User user, double positionX, double positionY, double positionZ) {
    UserMetaMovementData movementData = user.meta().movementData();
    World world = user.player().getWorld();
    float speed;
    if (movementData.lastOnGround) {
      Location location = new Location(
        world,
        WrappedMathHelper.floor(positionX),
        WrappedMathHelper.floor(positionY - movementData.frictionPosSubtraction()),
        WrappedMathHelper.floor(positionZ)
      );
      float slipperiness = resolveSlipperiness(user, location);
      float var4 = 0.16277136f / (slipperiness * slipperiness * slipperiness);
      speed = movementData.aiMoveSpeed() * var4;
    } else {
      speed = movementData.jumpMovementFactor();
    }
    return speed;
  }

  public static boolean isOffsetPositionInLiquid(
    Player player,
    WrappedAxisAlignedBB entityBoundingBox,
    double x, double y, double z
  ) {
    return isLiquidPresentInAABB(player, entityBoundingBox.offset(x, y, z));
  }

  private static boolean isLiquidPresentInAABB(Player player, WrappedAxisAlignedBB boundingBox) {
    List<WrappedAxisAlignedBB> collisionBoxes = Collision.resolve(player, boundingBox);
    return collisionBoxes.isEmpty() && !isAnyLiquid(player.getWorld(), boundingBox);
  }

  public static boolean isAnyLiquid(World world, WrappedAxisAlignedBB boundingBox) {
    int minX = WrappedMathHelper.floor(boundingBox.minX);
    int minY = WrappedMathHelper.floor(boundingBox.minY);
    int minZ = WrappedMathHelper.floor(boundingBox.minZ);
    int maxX = WrappedMathHelper.floor(boundingBox.maxX);
    int maxY = WrappedMathHelper.floor(boundingBox.maxY);
    int maxZ = WrappedMathHelper.floor(boundingBox.maxZ);
    for (int x = minX; x <= maxX; ++x) {
      for (int y = minY; y <= maxY; ++y) {
        for (int z = minZ; z <= maxZ; ++z) {
          Material material = BukkitBlockAccess.blockAccess(world, x, y, z).getType();
          if (MaterialLogic.isLiquid(material)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean isAllLiquid(World world, WrappedAxisAlignedBB boundingBox) {
    int minX = WrappedMathHelper.floor(boundingBox.minX);
    int minY = WrappedMathHelper.floor(boundingBox.minY);
    int minZ = WrappedMathHelper.floor(boundingBox.minZ);
    int maxX = WrappedMathHelper.floor(boundingBox.maxX);
    int maxY = WrappedMathHelper.floor(boundingBox.maxY);
    int maxZ = WrappedMathHelper.floor(boundingBox.maxZ);
    for (int x = minX; x <= maxX; ++x) {
      for (int y = minY; y <= maxY; ++y) {
        for (int z = minZ; z <= maxZ; ++z) {
          Material material = BukkitBlockAccess.blockAccess(world, x, y, z).getType();
          if (!MaterialLogic.isLiquid(material)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  public static boolean isLavaInBB(World world, WrappedAxisAlignedBB boundingBox) {
    int minX = WrappedMathHelper.floor(boundingBox.minX);
    int minY = WrappedMathHelper.floor(boundingBox.minY);
    int minZ = WrappedMathHelper.floor(boundingBox.minZ);
    int maxX = WrappedMathHelper.floor(boundingBox.maxX + 1.0D);
    int maxY = WrappedMathHelper.floor(boundingBox.maxY + 1.0D);
    int maxZ = WrappedMathHelper.floor(boundingBox.maxZ + 1.0D);
    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          if (MaterialLogic.isLava(BukkitBlockAccess.blockAccess(world, x, y, z).getType())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean isOnLadder(User user, double positionX, double positionY, double positionZ) {
    Player player = user.player();
    UserMetaClientData clientData = user.meta().clientData();
    Block block = BukkitBlockAccess.blockAccess(
      player.getWorld(),
      WrappedMathHelper.floor(positionX),
      WrappedMathHelper.floor(positionY),
      WrappedMathHelper.floor(positionZ)
    );
    Material type = block.getType();
    if (clientData.combatUpdate() && type.name().contains("TRAP_DOOR") && canGoThroughTrapDoorOnLadder(block)) {
      return true;
    }
    return type == Material.LADDER || type == Material.VINE;
  }

  private static boolean canGoThroughTrapDoorOnLadder(Block block) {
    Location location = block.getLocation();
    BlockState blockState = block.getState();
    MaterialData trapDoorData = blockState.getData();
    if (trapDoorData instanceof Openable && (((Openable) trapDoorData).isOpen())) {
      Attachable directional = (Attachable) blockState.getData();
      Location downLocation = location.clone().add(0, -1, 0);
      if (!(trapDoorData instanceof Directional)) {
        return false;
      }
      Block downBlock = BukkitBlockAccess.blockAccess(downLocation);
      MaterialData downBlockData = downBlock.getState().getData();
      if (!(downBlockData instanceof Directional)) {
        return false;
      }
      Directional downBlockDirectional = (Directional) downBlockData;
      return downBlock.getType() == Material.LADDER && directional.getFacing() == downBlockDirectional.getFacing();
    }
    return false;
  }
}