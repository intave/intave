package de.jpx3.intave.tools.client;

import de.jpx3.intave.annotate.refactoring.IdoNotBelongHere;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.EffectMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.blockphysic.BlockProperties;
import de.jpx3.intave.world.collision.Collision;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.wrapper.WrappedMathHelper;
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

@IdoNotBelongHere
public final class MovementContext {
  public static double jumpMotionFor(Player player, float jumpUpwardsMotion) {
    User user = UserRepository.userOf(player);
    EffectMetadata potionData = user.meta().potions();
    if (potionData.potionEffectJumpDuration > 0) {
      int jumpAmplifier = potionData.potionEffectJumpAmplifier();
      jumpUpwardsMotion += (float) ((jumpAmplifier + 1) * 0.1);
    }
    return jumpUpwardsMotion;
  }

  public static float resolveFriction(User user, double positionX, double positionY, double positionZ) {
    MovementMetadata movementData = user.meta().movement();
    World world = user.player().getWorld();
    float speed;
    if (movementData.lastOnGround) {
      Location location = new Location(
        world,
        WrappedMathHelper.floor(positionX),
        WrappedMathHelper.floor(positionY - movementData.frictionPosSubtraction()),
        WrappedMathHelper.floor(positionZ)
      );
      float slipperiness = currentSlipperiness(user, location);
      float var4 = movementData.frictionMultiplier() / (slipperiness * slipperiness * slipperiness);
      speed = movementData.aiMoveSpeed() * var4;
    } else {
      speed = movementData.jumpMovementFactor();
    }
    return speed;
  }

  public static float currentSlipperiness(User user, Location location) {
    Material type = BukkitBlockAccess.cacheAppliedTypeAccess(user, location);
    return BlockProperties.ofType(type).slipperiness() * 0.91f;
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
    return collisionBoxes.isEmpty() && !isAnyLiquid(player.getWorld(), UserRepository.userOf(player), boundingBox);
  }

  public static boolean isAnyLiquid(World world, User user, WrappedAxisAlignedBB boundingBox) {
    int minX = WrappedMathHelper.floor(boundingBox.minX);
    int minY = WrappedMathHelper.floor(boundingBox.minY);
    int minZ = WrappedMathHelper.floor(boundingBox.minZ);
    int maxX = WrappedMathHelper.floor(boundingBox.maxX);
    int maxY = WrappedMathHelper.floor(boundingBox.maxY);
    int maxZ = WrappedMathHelper.floor(boundingBox.maxZ);
    for (int x = minX; x <= maxX; ++x) {
      for (int y = minY; y <= maxY; ++y) {
        for (int z = minZ; z <= maxZ; ++z) {
          Material material = BukkitBlockAccess.cacheAppliedTypeAccess(user, world, x, y, z);
          if (Materials.isLiquid(material)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean isAllLiquid(User user, World world, WrappedAxisAlignedBB boundingBox) {
    int minX = WrappedMathHelper.floor(boundingBox.minX);
    int minY = WrappedMathHelper.floor(boundingBox.minY);
    int minZ = WrappedMathHelper.floor(boundingBox.minZ);
    int maxX = WrappedMathHelper.floor(boundingBox.maxX);
    int maxY = WrappedMathHelper.floor(boundingBox.maxY);
    int maxZ = WrappedMathHelper.floor(boundingBox.maxZ);
    for (int x = minX; x <= maxX; ++x) {
      for (int y = minY; y <= maxY; ++y) {
        for (int z = minZ; z <= maxZ; ++z) {
          Material material = BukkitBlockAccess.cacheAppliedTypeAccess(user, world, x, y, z);
          if (!Materials.isLiquid(material)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  public static boolean isLavaInBB(User user, World world, WrappedAxisAlignedBB boundingBox) {
    int minX = WrappedMathHelper.floor(boundingBox.minX);
    int minY = WrappedMathHelper.floor(boundingBox.minY);
    int minZ = WrappedMathHelper.floor(boundingBox.minZ);
    int maxX = WrappedMathHelper.floor(boundingBox.maxX + 1.0D);
    int maxY = WrappedMathHelper.floor(boundingBox.maxY + 1.0D);
    int maxZ = WrappedMathHelper.floor(boundingBox.maxZ + 1.0D);
    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          if (Materials.isLava(BukkitBlockAccess.cacheAppliedTypeAccess(user, world, x, y, z))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean isOnLadder(User user, double positionX, double positionY, double positionZ) {
    Player player = user.player();
    ProtocolMetadata clientData = user.meta().protocol();
    Block block = BukkitBlockAccess.blockAccess(
      player.getWorld(),
      WrappedMathHelper.floor(positionX),
      WrappedMathHelper.floor(positionY),
      WrappedMathHelper.floor(positionZ)
    );
    Material type = BukkitBlockAccess.cacheAppliedTypeAccess(
      user, player.getWorld(),
      WrappedMathHelper.floor(positionX),
      WrappedMathHelper.floor(positionY),
      WrappedMathHelper.floor(positionZ)
    );
    if (clientData.combatUpdate() && type.name().contains("TRAP_DOOR") && canGoThroughTrapDoorOnLadder(block)) {
      return true;
    }
    return BlockProperties.ofType(type).climbable();
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