package de.jpx3.intave.detect.checks.combat;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.service.entity.WrappedEntity;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.client.PlayerRotationHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.*;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import static de.jpx3.intave.event.service.entity.ClientSideEntityService.entityByIdentifier;
import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE;
import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_COMBAT_UPDATE;

public class AttackRaytrace extends IntaveMetaCheck<AttackRaytrace.AttackRaytraceMeta> {
  private final IntavePlugin plugin;

  public AttackRaytrace(IntavePlugin plugin) {
    super("AttackRaytrace", "attackRaytrace", AttackRaytraceMeta.class);
    this.plugin = plugin;
  }

  public static class AttackRaytraceMeta extends UserCustomCheckMeta {
    public int lastFlyPacketCounterReach = 0;
    public int lastAttackedEntityIDReach;
    public int confidence;
  }

  public static final class AttackRaytraceResult {
    public static AttackRaytraceResult.ResultType raytraceResultOf(double allowedReach, double value) {
      if (value == 10.0) {
        return AttackRaytraceResult.ResultType.MISS;
      }
      return value > allowedReach ? AttackRaytraceResult.ResultType.REACH : AttackRaytraceResult.ResultType.NORMAL;
    }

    public enum ResultType {
      NORMAL,
      REACH,
      MISS
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.EIGHTH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING")
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    AttackRaytraceMeta attackRaytraceMeta = metaOf(user);
    PacketContainer packet = event.getPacket();
    User.UserMeta meta = user.meta();
    UserMetaClientData clientData = meta.clientData();
    UserMetaMovementData movementData = meta.movementData();

    if (attackRaytraceMeta.lastAttackedEntityIDReach != -1) {
      WrappedEntity entity = entityByIdentifier(user, attackRaytraceMeta.lastAttackedEntityIDReach);

      if (entity != null && entity.checkable() && !player.isDead()) {
        if (clientData.protocolVersion() >= PROTOCOL_VERSION_COMBAT_UPDATE
          && movementData.pastFlyingPacketAccurate > 4
          && attackRaytraceMeta.lastFlyPacketCounterReach > 1) {
          processReachCheck(player, entity);
        } else if (clientData.protocolVersion() <= PROTOCOL_VERSION_BOUNTIFUL_UPDATE && attackRaytraceMeta.lastFlyPacketCounterReach > 1) {
          processReachCheck(player, entity);
        } else {
          //TODO: Old check
        }
      }

      attackRaytraceMeta.lastAttackedEntityIDReach = -1;
    }

    boolean hasMovement = packet.getBooleans().read(1);
    if (!hasMovement) {
      attackRaytraceMeta.lastFlyPacketCounterReach = 0;
    } else {
      attackRaytraceMeta.lastFlyPacketCounterReach++;
    }
  }

  private void processReachCheck(Player player, WrappedEntity entity) {
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    AttackRaytraceMeta attackRaytraceMeta = metaOf(user);
    UserMetaAttackData attackData = meta.attackData();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();

    double blockReachDistance = reachDistance(player.getGameMode() == GameMode.CREATIVE);
    float lastRotationYaw = movementData.lastRotationYaw % 360;
    float rotationYaw = movementData.rotationYaw;
    boolean alternativePositionY = clientData.protocolVersion() == UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE;

    // normal
    double reach = distanceOf(
      entity, alternativePositionY,
      movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
      lastRotationYaw, movementData.rotationPitch,
      movementData.sneaking
    );

    if (reach > blockReachDistance) {
      // mouse delay fix
      reach = distanceOf(
        entity, alternativePositionY,
        movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
        rotationYaw, movementData.rotationPitch,
        movementData.sneaking
      );
    }

    attackData.setLastReach(reach);
    String message;
    int vl;

    AttackRaytraceResult.ResultType attackRaytraceResult = AttackRaytraceResult.raytraceResultOf(blockReachDistance, reach);
    switch (attackRaytraceResult) {
      case MISS: {
        message = "missed hitbox on " + entity.entityName();
        vl = 1;
        break;
      }
      case REACH: {
        if (reach < 3.6 && attackRaytraceMeta.confidence++ == 0) {
          return;
        }
        String displayReach = MathHelper.formatDouble(reach, 4);
        message = "attacked " + entity.entityName() + " too far away (" + displayReach + " blocks)";
        vl = 6;
        break;
      }
      default: {
        return;
      }
    }

    plugin.retributionService().markPlayer(player, vl, "AttackRaytrace", message);
//    player.sendMessage("§6s:" + reach);
  }

  @PacketSubscription(
    priority = ListenerPriority.FIRST,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY")
    }
  )
  public void receiveUseEntityPacket(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    Player player = event.getPlayer();
    AttackRaytraceMeta attackRaytraceMeta = metaOf(player);
    EnumWrappers.EntityUseAction useAction = packet.getEntityUseActions().readSafely(0);
    if (useAction == EnumWrappers.EntityUseAction.ATTACK) {
      attackRaytraceMeta.lastAttackedEntityIDReach = packet.getIntegers().read(0);
    }
  }

  /**
   * Takes a entity and returns the range between the player and the entity. (Client side its called "getMouseOver" and
   * is from EntityRenderer.java)
   *
   * @return distance the distance between the entity and the eyes of the player 0 means the player is inside of the
   * entity -1 means the player hit outside of the hitbox of the entity >0 means the reach of the player
   */
  private double distanceOf(
    WrappedEntity entity, boolean alternativePositionY,
    double prevPosX, double prevPosY, double prevPosZ,
    float prevYaw, float pitch,
    boolean sneak
  ) {
    WrappedVector eyePosition = positionEyes(prevPosX, prevPosY, prevPosZ, sneak);
    double blockReachDistance = 6d;
    WrappedVector interpolatedLookVec = PlayerRotationHelper.wrappedVectorForRotation(pitch, prevYaw);
    WrappedVector rayCastedPosition = eyePosition.addVector(
      interpolatedLookVec.xCoord * blockReachDistance,
      interpolatedLookVec.yCoord * blockReachDistance,
      interpolatedLookVec.zCoord * blockReachDistance
    );
    WrappedAxisAlignedBB hitBox = entity.entityBoundingBox().expand(0.1f, 0.1f, 0.1f);
    if (alternativePositionY) {
      hitBox = hitBox.addJustMaxY(entity.alternativePositions.posY - entity.positions.posY);
    }
    WrappedMovingObjectPosition movingObjectPosition = hitBox.calculateIntercept(eyePosition, rayCastedPosition);
    if (hitBox.isVecInside(eyePosition)) {
      return 0;
    } else if (movingObjectPosition != null) {
      return eyePosition.distanceTo(movingObjectPosition.hitVec);
    }
    return 10;
  }

  private float reachDistance(boolean creative) {
    return creative ? 5.0F : 3.0F;
  }

  private static float eyeHeight(boolean sneak) {
    return sneak ? 1.54f : 1.62f;
  }

  private static WrappedVector positionEyes(double prevPosX, double prevPosY, double prevPosZ, boolean sneak) {
    return new WrappedVector(prevPosX, prevPosY + eyeHeight(sneak), prevPosZ);
  }
}