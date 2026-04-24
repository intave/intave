package de.jpx3.intave.check.world.interaction;

import de.jpx3.intave.check.world.InteractionRaytrace;
import de.jpx3.intave.share.ClientMath;
import de.jpx3.intave.share.MovingObjectPosition;
import de.jpx3.intave.share.NativeVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;

import static com.github.retrooper.packetevents.protocol.player.DiggingAction.CANCELLED_DIGGING;
import static de.jpx3.intave.check.world.interaction.InteractionType.EMPTY_INTERACT;

public class RaytraceEvaluation {
  private final InteractionRaytrace interactionRaytrace;
  private final Interaction origin;
  private final MovingObjectPosition raycastResult;
  private final boolean airHit;
  private final Location raycastLocation;
  private final Location targetLocation;

  public RaytraceEvaluation(
    InteractionRaytrace interactionRaytrace,
    Interaction origin,
    MovingObjectPosition raycastResult,
    boolean airHit,
    Location raycastLocation,
    Location targetLocation
  ) {
    this.interactionRaytrace = interactionRaytrace;
    this.origin = origin;
    this.raycastResult = raycastResult;
    this.airHit = airHit;
    this.raycastLocation = raycastLocation;
    this.targetLocation = targetLocation;
  }

  public Interaction origin() {
    return origin;
  }

  public MovingObjectPosition raycastResult() {
    return raycastResult;
  }

  public boolean airHit() {
    return airHit;
  }

  public Location raycastLocation() {
    return raycastLocation;
  }

  public Location targetLocation() {
    return targetLocation;
  }

  public boolean facingCheckFailed() {
    if (raycastResult != null && origin.hasFacing()) {
      float f = (float) (raycastResult.hitVec.xCoord - targetLocation.getX());
      float f1 = (float) (raycastResult.hitVec.yCoord - targetLocation.getY());
      float f2 = (float) (raycastResult.hitVec.zCoord - targetLocation.getZ());
      return Math.abs(compressAndDecompress(f) - origin.facingX()) > 0.01 ||
        Math.abs(compressAndDecompress(f1) - origin.facingY()) > 0.01 ||
        Math.abs(compressAndDecompress(f2) - origin.facingZ()) > 0.01;
    }
    return false;
  }

  private float compressAndDecompress(float f) {
    byte b = (byte) (int) (f * 16.0F);
    return (float) (b & 0xFF) / 16.0F;
  }


  public boolean hitMiss() {
    return raycastResult == null || raycastResult.hitVec == NativeVector.ZERO;
  }

  public boolean wrongBlockFace() {
    return wrongBlockFace(origin, raycastResult);
  }

  private boolean wrongBlockFace(Interaction interaction, MovingObjectPosition rayTraceResult) {
    Player player = interaction.player();
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();

    int sentIndex = interaction.targetDirectionIndex();
    int computedIndex = rayTraceResult == null ? sentIndex : rayTraceResult.sideHit.getIndex();

    if (protocol.oppositeBlockVectorBehavior()
      && interactionInHead(user, interaction)
      && sentIndex == rayTraceResult.sideHit.getOpposite().getIndex()) {
      return false;
    }

    // they don't send a block face here, don't make unnecessary adjustments
    if (interaction.digType() == CANCELLED_DIGGING && sentIndex == 0) {
      return false;
    }

    if (interaction.type() == EMPTY_INTERACT && sentIndex == 255) {
      return false;
    }

    return computedIndex != sentIndex;
  }

  private boolean interactionInHead(User user, Interaction interaction) {
    de.jpx3.intave.share.BlockPosition blockPosition = interaction.targetBlock();
    MovementMetadata movement = user.meta().movement();
    double xDiff = blockPosition.getX() - ClientMath.floor(movement.positionX);
    double yDiff = blockPosition.getY() - ClientMath.floor(movement.positionY + movement.eyeHeight());
    double zDiff = blockPosition.getZ() - ClientMath.floor(movement.positionZ);
    return xDiff == 0 && yDiff == 0 && zDiff == 0;
  }

  public boolean positionMismatch() {
    return positionMismatch(origin, raycastLocation, targetLocation);
  }

  private boolean positionMismatch(
    Interaction interaction,
    Location raycastLocation,
    Location targetLocation
  ) {
    if (interaction.type() == EMPTY_INTERACT) {
      return targetLocation.getBlockX() != -1 || targetLocation.getBlockY() != -1 || targetLocation.getBlockZ() != -1;
    }
    return raycastLocation.distance(targetLocation) > 0;
  }

  public boolean isValid() {
    return !isInvalid();
  }

  private Boolean invalidCache = null;

  public boolean isInvalid() {
    if (invalidCache != null) {
      return invalidCache;
    }
    return invalidCache = (hitMiss() || positionMismatch() || wrongBlockFace());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RaytraceEvaluation that = (RaytraceEvaluation) o;

    if (airHit != that.airHit) return false;
    if (!Objects.equals(origin, that.origin)) return false;
    if (!Objects.equals(raycastResult, that.raycastResult)) return false;
    if (!Objects.equals(raycastLocation, that.raycastLocation))
      return false;
    if (!Objects.equals(targetLocation, that.targetLocation))
      return false;
    return Objects.equals(invalidCache, that.invalidCache);
  }

  @Override
  public int hashCode() {
    int result = origin != null ? origin.hashCode() : 0;
    result = 31 * result + (raycastResult != null ? raycastResult.hashCode() : 0);
    result = 31 * result + (airHit ? 1 : 0);
    result = 31 * result + (raycastLocation != null ? raycastLocation.hashCode() : 0);
    result = 31 * result + (targetLocation != null ? targetLocation.hashCode() : 0);
    result = 31 * result + (invalidCache != null ? invalidCache.hashCode() : 0);
    return result;
  }
}
