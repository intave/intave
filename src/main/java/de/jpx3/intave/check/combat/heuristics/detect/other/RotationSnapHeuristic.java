package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.converter.BlockPositionConverter;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.ClientMath;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.world.raytrace.EntityRaytraceBlockConstraint;
import de.jpx3.intave.world.raytrace.Raytrace;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class RotationSnapHeuristic extends MetaCheckPart<Heuristics, RotationSnapHeuristic.RotationSnapHeuristicMeta> {

  public RotationSnapHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationSnapHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void receiveSwingPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    RotationSnapHeuristicMeta meta = metaOf(user);

    meta.lastSwing = 0;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      BLOCK_PLACE
    }
  )
  public void blockPlace(ProtocolPacketEvent event) {
    // moved to enabled() function at the bottom
//    if (MinecraftVersions.VER1_9_0.atOrAbove()) {
//      return;
//    }
    Player player = event.getPlayer();
    User user = userOf(player);

    // TODO: 01/28/21 Warning by Richy: The block-place is empty for native server versions from 1.9! Use the USE_ITEM packet instead
//    BlockPosition blockPosition = event.getPacket().getBlockPositionModifier().read(0);
    BlockPosition blockPosition = event.getPacket().getModifier()
      .withType(Lookup.serverClass("BlockPosition"), BlockPositionConverter.threadConverter())
      .read(0);
    int blockPlaceDirection = event.getPacket().getIntegers().read(0);

    if (blockPosition != null) {
      if (blockPlaceDirection != 255) {
        Material clickedType = VolatileBlockAccess.typeAccess(user, blockPosition.toLocation(player.getWorld()));
        boolean clickable = BlockInteractionAccess.isClickable(clickedType);

        if (!clickable) {
          RotationSnapHeuristicMeta meta = metaOf(user);
          meta.lastBlockPlace = 0;
        }
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void receiveAttackPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    RotationSnapHeuristicMeta meta = metaOf(user);

    PacketContainer packet = event.getPacket();
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
    if (action == null) {
      action = packet.getEnumEntityUseActions().read(0).getAction();
    }

    if (action == EnumWrappers.EntityUseAction.ATTACK) {
      meta.lastAttack = 0;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION_LOOK, LOOK
    }
  )
  public void receiveRotationPacket(ProtocolPacketEvent event) {
    metaOf(userOf(event.getPlayer())).rotationPacketCounter++;
  }

  private double keysToRotation(int strafe, int forward) {
    return Math.toDegrees(Math.atan2(strafe, forward)) - 90;
  }

  private double floorModDouble(double x, double y) {
    return (x - Math.floor(x / y) * y);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void receiveMovementPacket(ProtocolPacketEvent event) {
    // moved to enabled() function at the bottom
//    if (MinecraftVersions.VER1_9_0.atOrAbove()) {
//      return;
//    }
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();

    if (movementData.lastTeleport == 0) {
      return;
    }
    RotationSnapHeuristicMeta meta = metaOf(user);

    if (movementData.motionX() != 0 && movementData.motionZ() != 0) {
      meta.internalViolation -= 0.01;
      if (meta.internalViolation < 0)
        meta.internalViolation = 0;
    }

    double yawMotion = Math.abs(movementData.lastRotationYaw - movementData.rotationYaw);
    AttackMetadata attackData = user.meta().attack();

    if ((yawMotion > 40 && meta.yawMotions[1] < 9) || (yawMotion > 25 && meta.yawMotions[1] == 0)) {
      if (meta.lastKeyStrafe != movementData.keyStrafe || meta.lastKeyForward != movementData.keyForward) {
        double directionLast = movementData.rotationYaw + keysToRotation(meta.lastKeyStrafe, meta.lastKeyForward);
        double direction = movementData.lastRotationYaw + keysToRotation(movementData.keyStrafe, movementData.keyForward);

        direction = floorModDouble(direction, 360);
        directionLast = floorModDouble(directionLast, 360);

//      String key = resolveKeysFromInput(movementData.keyForward, movementData.keyStrafe);
//      String lastKey = resolveKeysFromInput(meta.lastKeyForward, meta.lastKeyStrafe);
        boolean silentMovement = (int) (ClientMath.wrapAngleTo180_double(directionLast - direction) / 45d) == 0;
        if (movementData.keyForward != meta.lastKeyForward || movementData.keyStrafe != meta.lastKeyStrafe) {
          if (silentMovement && (movementData.keyForward != 0 || movementData.keyStrafe != 0) && (meta.lastKeyForward != 0 || meta.lastKeyStrafe != 0)) {
            meta.silentMovements[0] = KeyStates.SILENTMOVE;
          } else {
            meta.silentMovements[0] = KeyStates.CHANGED;
          }
        }
      }

      Tick tick = new Tick(
        meta.lastLastPosX, meta.lastLastPosY, meta.lastLastPosZ,
        movementData.lastRotationYaw, movementData.lastRotationPitch
      );
      meta.movementAtTick[0] = tick;

//      for (Map.Entry<Integer, Entity> entry : user.meta().connection().entitiesById().entrySet()) {
//        Entity value = entry.getValue();
//        if (value != null && !(value instanceof Entity.Destroyed)) {
//          meta.entityPositions.put(entry.getKey(), value.positionHistory.get(Math.max(value.positionHistory.size() - 1, 0)));
//        }
//      }

      for (Entity tracedEntity : user.meta().connection().tracedEntities()) {
        if (tracedEntity != null && !(tracedEntity instanceof Entity.Destroyed)) {
//          try {
//            tracedEntity.positionHistoryLock.lock();
//            meta.entityPositions.put(tracedEntity.entityId(), tracedEntity.immediateServerPosition);
//          } finally {
//            tracedEntity.positionHistoryLock.unlock();
//          }
        }
      }
    }

    boolean isSuspicious = (meta.yawMotions[1] == 0 && meta.yawMotions[0] > 25 && meta.yawMotions[0] > 9);
    boolean liteFlag = isSuspicious && meta.silentMovements[1] == KeyStates.SILENTMOVE && meta.rotationPacketCounter > 10 && movementData.lastTeleport > 7;

    isSuspicious = meta.yawMotions[1] < 9 && meta.yawMotions[0] > 40 && yawMotion < 9;

    if (isSuspicious && (meta.lastSwing <= 3 || meta.lastAttack <= 3) && meta.rotationPacketCounter > 10 && movementData.lastTeleport > 7) {
      double valueOfSnap = meta.yawMotions[0];
      String description = "rotation snap ["
        + MathHelper.formatDouble(meta.yawMotions[1], 2)
        + "/" + MathHelper.formatDouble(meta.yawMotions[0], 2)
        + "/" + MathHelper.formatDouble(yawMotion, 2) + "]";

      if (meta.silentMovements[1] == KeyStates.SILENTMOVE) {
        description += " silent";
      } else if (meta.silentMovements[1] == KeyStates.CHANGED) {
        description += " changed";
      }

      boolean changedLookToEntity = false;
      if (attackData.lastAttackedEntity() != null && attackData.lastAttackedEntity().positionHistory.size() > 2) {
        Entity entity = attackData.lastAttackedEntity();

        Tick tick = meta.movementAtTick[1];
//        Map<Integer, Entity.EntityPositionContext> entityPositions = meta.entityPositions;
//        Entity.EntityPositionContext lastEntityPosition = entityPositions.get(entity.entityId());

        if (/*lastEntityPosition != null && */tick != null) {
          BoundingBox lastBoundingBox = null;//Entity.entityBoundingBoxFrom(lastEntityPosition, entity);
          Raytrace last = Raytracing.entityRaytrace(
            player,
            lastBoundingBox,
            0,
            tick.posX, tick.posY, tick.posZ,
            tick.yaw, tick.pitch,
            0.1f,
            EntityRaytraceBlockConstraint.IGNORE_BLOCKS
          );

          Raytrace raytrace = Raytracing.entityRaytrace(
            player,
            entity.boundingBox(),
            0,
            movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
            movementData.lastRotationYaw, movementData.lastRotationPitch,
            0.1f,
            EntityRaytraceBlockConstraint.IGNORE_BLOCKS
          );

          changedLookToEntity = (last.reach() != 10) != (raytrace.reach() != 10);
          if (changedLookToEntity) {
            description += " lookEn";
          }
        }
      }

      double vl = calculateViolation(valueOfSnap, changedLookToEntity, user, liteFlag);
      liteFlag = false;

      // this mitigation has become too obvious, and is required for detection
      //dmc23
      if (vl >= 40) {
//        user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, "23");
      }
      if (vl > 70) {
//        user.applyAttackNerfer(AttackNerfStrategy.CANCEL_FIRST_HIT, "23");
      }

      handleConfidence(user, "102", (int) vl, description);

//      meta.entityPositions.clear();
    }

    if (liteFlag) {
      String description = "rotation snap scaffold [" + MathHelper.formatDouble(meta.yawMotions[0], 2) + "]";
      int addedViolationLevel = 30;
      handleConfidence(user, "103", addedViolationLevel, description);
    }

    prepareNextTick(meta, yawMotion, user);
  }

  private void handleConfidence(User user, String key, int violationToAdd, String description) {
    RotationSnapHeuristicMeta meta = metaOf(user);
    Player player = user.player();

    meta.internalViolation += violationToAdd;
    Confidence confidence = Confidence.confidenceFrom(meta.internalViolation);

    if (confidence.level() >= 30) {
      meta.internalViolation -= confidence.level();
      if (user.protocolVersion() > 47) {
        description += " " + user.protocolVersion();
      }

      description += " conf:" + confidence.level() + "/" + meta.internalViolation;
      Anomaly anomaly = Anomaly.anomalyOf(key, confidence, Anomaly.Type.KILLAURA, description, anomalyOptions(isPartner()));
      parentCheck().saveAnomaly(player, anomaly);
    } else if (confidence.level() > 0) {
      description += " nonflag(" + violationToAdd + "/" + confidence.level() + "/" + meta.internalViolation + ")";
      Anomaly anomaly = Anomaly.anomalyOf(key, Confidence.NONE, Anomaly.Type.KILLAURA, description, anomalyOptions(isPartner()));
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  // I can not test this
  public boolean isPartner() {
    return (ProtocolMetadata.VERSION_DETAILS & 0x100) != 0;
  }

  private int anomalyOptions(boolean isPartner) {
    return LIMIT_4 | LIMIT_2 | LIMIT_1;
  }

  private double calculateViolation(double valueOfSnap, boolean changedLookToEntity, User user, boolean liteFlag) {
    RotationSnapHeuristicMeta meta = metaOf(user);
    double vl = 7;
    if (valueOfSnap > 360) {
      vl = 120;
    } else if (valueOfSnap > 178) {
      vl = 50;
    } else if (valueOfSnap > 90) {
      vl = 20;
    } else if (valueOfSnap > 50) {
      vl = 10;
    }
    if (meta.lastBlockPlace < 3) {
      vl *= 1.5;
    }
    if (changedLookToEntity) {
      vl *= 2;
    }
    if (meta.silentMovements[1] == KeyStates.SILENTMOVE) {
      vl *= 3;
    } else if (meta.silentMovements[1] == KeyStates.CHANGED) {
      vl *= 1.7;
    }
    if (liteFlag) {
      vl += 10;
    }
    // added the division because there are false flaggs when a player has less than 20 fps
    vl /= 3;
    if (vl > 160 && valueOfSnap < 360) {
      vl = 160;
    }
    return vl;
  }

  private void prepareNextTick(RotationSnapHeuristicMeta meta, double yawMotion, User user) {
    MovementMetadata movementData = user.meta().movement();
    meta.lastKeyForward = movementData.keyForward;
    meta.lastKeyStrafe = movementData.keyStrafe;

    meta.lastLastPosX = movementData.lastPositionX;
    meta.lastLastPosY = movementData.lastPositionY;
    meta.lastLastPosZ = movementData.lastPositionZ;

    meta.yawMotions[1] = meta.yawMotions[0];
    meta.yawMotions[0] = yawMotion;

    meta.silentMovements[1] = meta.silentMovements[0];
    meta.silentMovements[0] = KeyStates.NONE;

    meta.movementAtTick[1] = meta.movementAtTick[0];
    meta.movementAtTick[0] = null;

    meta.lastSwing++;
    meta.lastAttack++;
    meta.lastBlockPlace++;
  }

  @Override
  public boolean enabled() {
    return !MinecraftVersions.VER1_9_0.atOrAbove() && super.enabled();
  }

  public static final class RotationSnapHeuristicMeta extends CheckCustomMetadata {
    double lastLastPosX, lastLastPosY, lastLastPosZ;
//    Map<Integer, Po> entityPositions = new HashMap<>();
    private final Tick[] movementAtTick = new Tick[2];
    private final double[] yawMotions = new double[2];
    private final KeyStates[] silentMovements = new KeyStates[2];
    private int internalViolation;
    private int lastKeyForward;
    private int lastKeyStrafe;
    // used to disable the check on startup
    private int rotationPacketCounter;
    private int lastSwing;
    private int lastAttack;
    private int lastBlockPlace;
  }

  enum KeyStates {
    NONE, CHANGED, SILENTMOVE
  }

  static class Tick {
    double posX, posY, posZ;
    float yaw, pitch;

    public Tick(double posX, double posY, double posZ, float yaw, float pitch) {
      this.posX = posX;
      this.posY = posY;
      this.posZ = posZ;

      this.yaw = yaw;
      this.pitch = pitch;
    }
  }
}
