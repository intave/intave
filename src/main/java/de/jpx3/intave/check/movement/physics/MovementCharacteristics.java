package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.EffectMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import static de.jpx3.intave.block.collision.Collision.rasterizedLiquidPresentEnforcement;
import static de.jpx3.intave.share.ClientMath.floor;

public final class MovementCharacteristics {
  public static double jumpMotionFor(User user, float jumpUpwardsMotion) {
    boolean infiniteEffectsAllowed = user.protocolVersion() >= 763;
    EffectMetadata potionData = user.meta().potions();
    int potionDuration = potionData.potionEffectJumpDuration;
    if (potionDuration > 0 || potionDuration == -1 && infiniteEffectsAllowed) {
      int jumpAmplifier = potionData.potionEffectJumpAmplifier();
      jumpUpwardsMotion += (float) ((jumpAmplifier + 1) * 0.1);
    }
    return jumpUpwardsMotion;
  }

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

  public static float currentSlipperiness(User user, World world, double blockPositionX, double blockPositionY, double blockPositionZ) {
    Material type;

    boolean improvedSlipperiness = user.meta().protocol().trailsAndTailsUpdate();
    if (improvedSlipperiness) {
      type = user.meta().movement().frictionMaterial();
    } else {
      type = VolatileBlockAccess.typeAccess(user, world, blockPositionX, blockPositionY, blockPositionZ);
    }

    return BlockProperties.of(type).slipperiness() * 0.91f;
  }

  public static boolean isOffsetPositionInLiquid(
    User user, BoundingBox entityBoundingBox,
    double x, double y, double z
  ) {
    BoundingBox boundingBox = entityBoundingBox.offset(x, y, z);
    return Collision.nonePresent(user, user.meta().movement(), boundingBox) && !rasterizedLiquidPresentEnforcement(user, boundingBox);
  }

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