package de.jpx3.intave.detect.checks.combat;

import com.comphenix.protocol.ProtocolLibrary;
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
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.event.service.entity.ClientSideEntityService.entityByIdentifier;
import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE;
import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_COMBAT_UPDATE;

public class AttackRaytrace extends IntaveMetaCheck<AttackRaytrace.AttackRaytraceMeta> {
  private final IntavePlugin plugin;

  public AttackRaytrace(IntavePlugin plugin) {
    super("AttackRaytrace", "attackRaytrace", AttackRaytraceMeta.class);
    this.plugin = plugin;
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
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
      PacketContainer packetClone = packet.deepClone();
      int entityId =  packet.getIntegers().read(0);
      Attack attack = new Attack(packetClone, entityId);
      if(attackRaytraceMeta.remainingAttacks.size() < 4) {
        attackRaytraceMeta.remainingAttacks.add(attack);
      }
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
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

    List<Attack> remainingAttacks = attackRaytraceMeta.remainingAttacks;

//    player.sendMessage(ChatColor.GRAY + "" + System.nanoTime());

    if(!remainingAttacks.isEmpty()) {
//      int attacks = remainingAttacks.size();
//      long duration = 0;

//      player.sendMessage(String.valueOf(diff));
      for (Attack remainingAttack : remainingAttacks) {
        WrappedEntity entity = entityByIdentifier(user, remainingAttack.entityId());

        boolean invalid = false;

        if (entity != null && entity.checkable() && !player.isDead()) {
          if (entity.clientSynchronized && clientData.protocolVersion() >= PROTOCOL_VERSION_COMBAT_UPDATE
            && movementData.pastFlyingPacketAccurate > 4
            && attackRaytraceMeta.lastFlyPacketCounterReach > 1
          ) {
            invalid = processReachCheck(player, entity);
          } else if (entity.clientSynchronized && clientData.protocolVersion() <= PROTOCOL_VERSION_BOUNTIFUL_UPDATE && attackRaytraceMeta.lastFlyPacketCounterReach > 1) {
            invalid = processReachCheck(player, entity);
          } else {
            //TODO: Old check
//            iterative = true;
            invalid = processIterativeReachCheck(player, entity);
          }
        }
//        duration += System.nanoTime() - start;

        if(!invalid) {
          receiveExcludedPacket(player, remainingAttack.packet);
        }
      }

//      player.sendMessage(duration + " ns | " + attacks);
      remainingAttacks.clear();
    }

    boolean hasMovement = packet.getBooleans().read(1);
    if (!hasMovement) {
      attackRaytraceMeta.lastFlyPacketCounterReach = 0;
    } else {
      attackRaytraceMeta.lastFlyPacketCounterReach++;
    }
  }

  private void receiveExcludedPacket(Player player, PacketContainer packet) {
    try {
      userOf(player).ignoreNextPacket();
      ProtocolLibrary.getProtocolManager().recieveClientPacket(player, packet);
    } catch (InvocationTargetException | IllegalAccessException exception) {
      exception.printStackTrace();
    }
  }

  private boolean processReachCheck(Player player, WrappedEntity entity) {
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
      player,
      entity, alternativePositionY,
      movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
      lastRotationYaw, movementData.rotationPitch,
      movementData.sneaking
    );

    if (reach > blockReachDistance) {
      // mouse delay fix
      reach = distanceOf(
        player,
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
    String entityName = entity.entityName();
    switch (attackRaytraceResult) {
      case MISS: {
        message = "missed hitbox of " + resolveIndefArticle(entityName) + " " + entityName.toLowerCase();
        vl = 1;
        break;
      }
      case REACH: {
        if (reach < 3.6 && attackRaytraceMeta.confidence++ == 0) {
          return false;
        }
        String displayReach = MathHelper.formatDouble(reach, 4);
        message = "attacked " + resolveIndefArticle(entityName) + " " + entityName.toLowerCase() + " too far away (" + displayReach + " blocks)";
        vl = 6;
        break;
      }
      default: {
        return false;
      }
    }

    plugin.retributionService().markPlayer(player, vl, "AttackRaytrace", message);
//    player.sendMessage("§6s:" + reach);
    return true;
  }

  private boolean processIterativeReachCheck(Player player, WrappedEntity entity) {
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();

    double blockReachDistance = reachDistance(player.getGameMode() == GameMode.CREATIVE);
    float lastRotationYaw = movementData.lastRotationYaw % 360;
    float rotationYaw = movementData.rotationYaw;
    boolean alternativePositionY = clientData.protocolVersion() == UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE;

    double minReach = 10;
    double maxReach = 0;

    WrappedEntity.EntityPositionContext oldPosition = entity.position.clone();
    WrappedEntity.EntityPositionContext oldAlternativePosition = entity.alternativePosition.clone();

    int index = 0;

    for (WrappedEntity.EntityPositionContext possiblePosition : entity.possiblePositions) {
      entity.position = possiblePosition.clone();
      entity.alternativePosition = entity.possibleAlternativePositions.get(index).clone();

      // TODO: 01/07/21 add trust-factor based length tolerance

      int originalNewPosRotationIncrements = entity.newPosRotationIncrements;
      entity.newPosRotationIncrements = 3;

      double minReachInItr = 10;
      double maxReachInItr = 0;

      for (int i = 0; i < 4; i++) {
        // normal
        double reach = distanceOf(
          player,
          entity.entityBoundingBox().grow(0.15),
          entity.position, entity.alternativePosition,
          alternativePositionY,
          movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
          lastRotationYaw, movementData.rotationPitch,
          movementData.sneaking
        );

        if (reach > blockReachDistance) {
          // mouse delay fix
          reach = distanceOf(
            player,
            entity.entityBoundingBox().grow(0.15),
            entity.position, entity.alternativePosition,
            alternativePositionY,
            movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
            rotationYaw, movementData.rotationPitch,
            movementData.sneaking
          );
        }

        minReachInItr = Math.min(minReachInItr, reach);
        maxReachInItr = Math.max(maxReachInItr, reach);

        entity.onLivingUpdate();
      }

//      player.sendMessage(MathHelper.formatMotion(new Vector(possiblePosition.posX, possiblePosition.posY, possiblePosition.posZ)) + " " + minReachInItr + " " + maxReachInItr);

      minReach = Math.min(minReach, minReachInItr);
      maxReach = Math.max(maxReach, maxReachInItr);

      entity.newPosRotationIncrements = originalNewPosRotationIncrements;
      index++;
    }

    // TODO: 01/07/21 clear after last possible position

    entity.position = oldPosition;
    entity.alternativePosition = oldAlternativePosition;

    if(minReach > blockReachDistance) {
      String entityName = entity.entityName();
      String targetDescriptor = resolveIndefArticle(entityName) + " " + entityName.toLowerCase();

      String message;
      if(minReach == 10) {
        message = "missed hitbox of "+targetDescriptor+" (estimated)";
      } else {
        String minReachDisplay = MathHelper.formatDouble(minReach, 4) + " blocks";
//        String maxReachDisplay = maxReach == 10 ? "miss" : MathHelper.formatDouble(maxReach, 4) + " blocks";
        message = "attacked "+targetDescriptor+" too far away (estimated) (" + minReachDisplay + " at best)";
      }
      plugin.retributionService().markPlayer(player, 0, "AttackRaytrace", message);
      return true;
    }
    return false;
  }

  private final static char[] vocals = "aeiou".toCharArray();

  private String resolveIndefArticle(String exceptionName) {
    char c = exceptionName.toCharArray()[0];
    boolean isVocal = false;
    for (char vocal : vocals) {
      if (vocal == c) {
        isVocal = true;
        break;
      }
    }
    return isVocal ? "an" : "a";
  }

  private double distanceOf(
    Player player, WrappedEntity entity,
    boolean alternativePositionY,
    double prevPosX, double prevPosY, double prevPosZ,
    float prevYaw, float pitch,
    boolean sneak
  ) {
    return distanceOf(
      player,
      entity.entityBoundingBox(),
      entity.position, entity.alternativePosition,
      alternativePositionY,
      prevPosX, prevPosY, prevPosZ,
      prevYaw, pitch,
      sneak
    );
  }

  /**
   * Takes a entity and returns the range between the player and the entity. (Client side its called "getMouseOver" and
   * is from EntityRenderer.java)
   *
   * @return distance the distance between the entity and the eyes of the player 0 means the player is inside of the
   * entity -1 means the player hit outside of the hitbox of the entity >0 means the reach of the player
   */
  private double distanceOf(
    Player player,
    WrappedAxisAlignedBB entityBoundingBox,
    WrappedEntity.EntityPositionContext position,
    WrappedEntity.EntityPositionContext alternativePosition,
    boolean alternativePositionY,
    double prevPosX, double prevPosY, double prevPosZ,
    float prevYaw, float pitch,
    boolean sneak
  ) {
    WrappedVector eyeVector = positionEyes(prevPosX, prevPosY, prevPosZ, sneak);
    double blockReachDistance = 6d;
    WrappedVector interpolatedLookVec = PlayerRotationHelper.wrappedVectorForRotation(pitch, prevYaw);
    WrappedVector lookVector = eyeVector.addVector(
      interpolatedLookVec.xCoord * blockReachDistance,
      interpolatedLookVec.yCoord * blockReachDistance,
      interpolatedLookVec.zCoord * blockReachDistance
    );

    WrappedAxisAlignedBB hitBox = entityBoundingBox.expand(0.1f, 0.1f, 0.1f);
    if (alternativePositionY) {
      hitBox = hitBox.addJustMaxY(alternativePosition.posY - position.posY);
    }
    WrappedMovingObjectPosition movingObjectPosition = hitBox.calculateIntercept(eyeVector, lookVector);
    if (hitBox.isVecInside(eyeVector)) {
      return 0;
    } else if (movingObjectPosition != null) {
      WrappedMovingObjectPosition blockMovingPosition = Raytracer.blockRayTrace(player.getWorld(), player, eyeVector, lookVector);

      double distanceToBlock = blockMovingPosition == null || blockMovingPosition.hitVec == null ? 10 : eyeVector.distanceTo(blockMovingPosition.hitVec);
      double distanceToEntity = eyeVector.distanceTo(movingObjectPosition.hitVec);

      return distanceToBlock < distanceToEntity ? 10 : distanceToEntity;
    }
    return 10;
  }

  private float reachDistance(boolean creative) {
    return (creative ? 5.0F : 3.0F) + 0.001f;
  }

  private static float eyeHeight(boolean sneak) {
    return sneak ? 1.54f : 1.62f;
  }

  private static WrappedVector positionEyes(double prevPosX, double prevPosY, double prevPosZ, boolean sneak) {
    return new WrappedVector(prevPosX, prevPosY + eyeHeight(sneak), prevPosZ);
  }

  public static class AttackRaytraceMeta extends UserCustomCheckMeta {
    public int lastFlyPacketCounterReach = 0;
    public List<Attack> remainingAttacks = new ArrayList<>();
    public long lastTimeAttackedEntity;
    public int confidence;
  }

  public static class Attack {
    private final PacketContainer packet;
    private final int entityId;


    public Attack(PacketContainer packet, int entityId) {
      this.packet = packet;
      this.entityId = entityId;
    }

    public PacketContainer packet() {
      return packet;
    }

    public int entityId() {
      return entityId;
    }
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
}