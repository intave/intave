package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.annotate.refactoring.IdoNotBelongHere;
import de.jpx3.intave.block.access.BlockWrapper;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.EffectMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
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

import static de.jpx3.intave.shade.WrappedMathHelper.floor;

public final class MovementHelper {
  @Deprecated
  @IdoNotBelongHere
  public static double jumpMotionFor(Player player, float jumpUpwardsMotion) {
    User user = UserRepository.userOf(player);
    EffectMetadata potionData = user.meta().potions();
    if (potionData.potionEffectJumpDuration > 0) {
      int jumpAmplifier = potionData.potionEffectJumpAmplifier();
      jumpUpwardsMotion += (float) ((jumpAmplifier + 1) * 0.1);
    }
    return jumpUpwardsMotion;
  }

  @Deprecated
  @IdoNotBelongHere
  public static float resolveFriction(User user, double positionX, double positionY, double positionZ) {
    MovementMetadata movementData = user.meta().movement();
    World world = user.player().getWorld();
    float speed;
    if (movementData.lastOnGround) {
      Location location = new Location(
        world,
        floor(positionX),
        floor(positionY - movementData.frictionPosSubtraction()),
        floor(positionZ)
      );
      float slipperiness = currentSlipperiness(user, location);
      float var4 = movementData.frictionMultiplier() / (slipperiness * slipperiness * slipperiness);
      speed = movementData.aiMoveSpeed() * var4;
    } else {
      speed = movementData.jumpMovementFactor();
    }
    return speed;
  }

  @Deprecated
  @IdoNotBelongHere
  public static float currentSlipperiness(User user, Location location) {
    Material type = VolatileBlockAccess.typeAccess(user, location);
    return BlockProperties.ofType(type).slipperiness() * 0.91f;
  }

  @Deprecated
  @IdoNotBelongHere
  public static boolean isOffsetPositionInLiquid(
    Player player,
    BoundingBox entityBoundingBox,
    double x, double y, double z
  ) {
    return isLiquidPresentInAABB(player, entityBoundingBox.offset(x, y, z));
  }

  @Deprecated
  @IdoNotBelongHere
  private static boolean isLiquidPresentInAABB(Player player, BoundingBox boundingBox) {
    return Collision.nonePresent(player, boundingBox) && !isAnyLiquid(player.getWorld(), UserRepository.userOf(player), boundingBox);
  }

  @Deprecated
  @IdoNotBelongHere
  public static boolean isAnyLiquid(World world, User user, BoundingBox boundingBox) {
    int minX = floor(boundingBox.minX);
    int minY = floor(boundingBox.minY);
    int minZ = floor(boundingBox.minZ);
    int maxX = floor(boundingBox.maxX);
    int maxY = floor(boundingBox.maxY);
    int maxZ = floor(boundingBox.maxZ);
    for (int x = minX; x <= maxX; ++x) {
      for (int y = minY; y <= maxY; ++y) {
        for (int z = minZ; z <= maxZ; ++z) {
          Material material = VolatileBlockAccess.typeAccess(user, world, x, y, z);
          if (MaterialMagic.isLiquid(material)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Deprecated
  @IdoNotBelongHere
  public static boolean isAllLiquid(User user, World world, BoundingBox boundingBox) {
    int minX = floor(boundingBox.minX);
    int minY = floor(boundingBox.minY);
    int minZ = floor(boundingBox.minZ);
    int maxX = floor(boundingBox.maxX);
    int maxY = floor(boundingBox.maxY);
    int maxZ = floor(boundingBox.maxZ);
    for (int x = minX; x <= maxX; ++x) {
      for (int y = minY; y <= maxY; ++y) {
        for (int z = minZ; z <= maxZ; ++z) {
          Material material = VolatileBlockAccess.typeAccess(user, world, x, y, z);
          if (!MaterialMagic.isLiquid(material)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  @Deprecated
  @IdoNotBelongHere
  public static boolean isLavaInBB(User user, World world, BoundingBox boundingBox) {
    int minX = floor(boundingBox.minX);
    int minY = floor(boundingBox.minY);
    int minZ = floor(boundingBox.minZ);
    int maxX = floor(boundingBox.maxX + 1.0D);
    int maxY = floor(boundingBox.maxY + 1.0D);
    int maxZ = floor(boundingBox.maxZ + 1.0D);
    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          if (MaterialMagic.isLava(VolatileBlockAccess.typeAccess(user, world, x, y, z))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Deprecated
  @IdoNotBelongHere
  public static boolean isOnLadder(User user, double positionX, double positionY, double positionZ) {
    Player player = user.player();
    ProtocolMetadata clientData = user.meta().protocol();
    Block block = VolatileBlockAccess.unsafe__BlockAccess(
      player.getWorld(),
      floor(positionX),
      floor(positionY),
      floor(positionZ)
    );
    Material type = VolatileBlockAccess.typeAccess(
      user, player.getWorld(),
      floor(positionX),
      floor(positionY),
      floor(positionZ)
    );
    if (clientData.combatUpdate() && ItemProperties.isTrapdoor(type) && canGoThroughTrapDoorOnLadder(user, block)) {
      return true;
    }
    return BlockProperties.ofType(type).climbable();
  }

  @Deprecated
  @IdoNotBelongHere
  private static boolean canGoThroughTrapDoorOnLadder(User user, Block block) {
    block = BlockWrapper.emit(user, block);
    Location location = block.getLocation();
    BlockState blockState = block.getState(); // unbelievable heavy
    MaterialData trapDoorData = blockState.getData();
    if (trapDoorData instanceof Openable && (((Openable) trapDoorData).isOpen())) {
      Attachable directional = (Attachable) blockState.getData();
      Location downLocation = location.clone().add(0, -1, 0);
      if (!(trapDoorData instanceof Directional)) {
        return false;
      }
      Block downBlock = VolatileBlockAccess.unsafe__BlockAccess(downLocation);
      MaterialData downBlockData = downBlock.getState().getData();
      if (!(downBlockData instanceof Directional)) {
        return false;
      }
      Directional downBlockDirectional = (Directional) downBlockData;
      return VolatileBlockAccess.typeAccess(user, downLocation) == Material.LADDER && directional.getFacing() == downBlockDirectional.getFacing();
    }
    return false;
  }
}