package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.annotate.refactoring.IdoNotBelongHere;
import de.jpx3.intave.annotate.refactoring.WhyMustIExist;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.EffectMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import static de.jpx3.intave.share.ClientMathHelper.floor;

@Deprecated
@WhyMustIExist
public final class MovementCharacteristics {
  @Deprecated
  @IdoNotBelongHere
  @WhyMustIExist
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
  @WhyMustIExist
  public static float resolveFriction(User user, boolean sprinting, double positionX, double positionY, double positionZ) {
    MovementMetadata movementData = user.meta().movement();
    World world = user.player().getWorld();
    float speed;
    if (movementData.lastOnGround) {
      float slipperiness = currentSlipperiness(
        user,
        world,
        positionX,
        positionY - movementData.frictionPosSubtraction(),
        positionZ
      );
      float var4 = movementData.frictionMultiplier() / (slipperiness * slipperiness * slipperiness);
      speed = movementData.aiMoveSpeed(sprinting) * var4;
    } else {
      speed = movementData.jumpMovementFactor();
    }
    return speed;
  }

  @Deprecated
  @IdoNotBelongHere
  @WhyMustIExist
  public static float currentSlipperiness(User user, Location location) {
    Material type = VolatileBlockAccess.typeAccess(user, location);
    return BlockProperties.of(type).slipperiness() * 0.91f;
  }

  @Deprecated
  @IdoNotBelongHere
  @WhyMustIExist
  public static float currentSlipperiness(User user, World world, double blockPositionX, double blockPositionY, double blockPositionZ) {
    Material type = VolatileBlockAccess.typeAccess(user, world, blockPositionX, blockPositionY, blockPositionZ);
    return BlockProperties.of(type).slipperiness() * 0.91f;
  }

  @Deprecated
  @IdoNotBelongHere
  @WhyMustIExist
  public static boolean isOffsetPositionInLiquid(
    Player player,
    BoundingBox entityBoundingBox,
    double x, double y, double z
  ) {
    return isLiquidPresentInAABB(player, entityBoundingBox.offset(x, y, z));
  }

  @Deprecated
  @IdoNotBelongHere
  @WhyMustIExist
  private static boolean isLiquidPresentInAABB(Player player, BoundingBox boundingBox) {
    return Collision.nonePresent(player, boundingBox) && !isAnyLiquid(player.getWorld(), UserRepository.userOf(player), boundingBox);
  }

  @Deprecated
  @IdoNotBelongHere
  @WhyMustIExist
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
  @WhyMustIExist
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
  @WhyMustIExist
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
  @WhyMustIExist
  public static boolean onClimbable(User user, double positionX, double positionY, double positionZ) {
    Player player = user.player();
    ProtocolMetadata clientData = user.meta().protocol();
    Material type = VolatileBlockAccess.typeAccess(
      user, player.getWorld(),
      floor(positionX),
      floor(positionY),
      floor(positionZ)
    );
    if (clientData.combatUpdate() && ItemProperties.isTrapdoor(type) && canGoThroughTrapDoorOnLadder(user, positionX, positionY, positionZ)) {
      return true;
    }
    return BlockProperties.of(type).climbable();
  }

  @Deprecated
  @IdoNotBelongHere
  @WhyMustIExist
  private static boolean canGoThroughTrapDoorOnLadder(User user, double positionX, double positionY, double positionZ) {
    BlockVariant variant = VolatileBlockAccess.variantAccess(user, user.player().getWorld(), positionX, positionY, positionZ);
    boolean isOpen = variant.propertyOf("open");
    if (isOpen) {
      Direction direction = variant.enumProperty(Direction.class, "facing");
      if (VolatileBlockAccess.typeAccess(user, user.player().getWorld(), positionX, positionY - 1, positionZ) != Material.LADDER) {
        return false;
      }
      BlockVariant variantBelow = VolatileBlockAccess.variantAccess(user, user.player().getWorld(), positionX, positionY - 1, positionZ);
      Direction directionBelow = variantBelow.enumProperty(Direction.class, "facing");
      return direction != null && direction == directionBelow;
    }
    return false;
  }
}