package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.punishment.AttackNerfStrategy;
import de.jpx3.intave.event.service.entity.WrappedEntity;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

import static de.jpx3.intave.world.raytrace.Raytracer.distanceOf;

public class RotationSnapHeuristic extends IntaveMetaCheckPart<Heuristics, RotationSnapHeuristic.RotationSnapHeuristicMeta> {

  public RotationSnapHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationSnapHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "ARM_ANIMATION")
    }
  )
  public void receiveSwingPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    RotationSnapHeuristicMeta meta = metaOf(user);

    meta.lastSwing = 0;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE")
    }
  )
  public void blockPlace(PacketEvent event) {
    if (ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0)) {
      return;
    }
    Player player = event.getPlayer();
    User user = userOf(player);

    // TODO: 01/28/21 Warning by Richy: The block-place is empty for native server versions from 1.9! Use the USE_ITEM packet instead
    BlockPosition blockPosition = event.getPacket().getBlockPositionModifier().read(0);
    int blockPlaceDirection = event.getPacket().getIntegers().read(0);

    if (blockPosition != null) {
      if (blockPlaceDirection != 255) {
        Material clickedType = BukkitBlockAccess.blockAccess(blockPosition.toLocation(player.getWorld())).getType();
        boolean clickable = BlockDataAccess.isClickable(clickedType);

        if (!clickable) {
          RotationSnapHeuristicMeta meta = metaOf(user);
          meta.lastBlockPlace = 0;
        }
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY")
    }
  )
  public void receiveAttackPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    RotationSnapHeuristicMeta meta = metaOf(user);

    EnumWrappers.EntityUseAction entityUseAction = event.getPacket().getEntityUseActions().read(0);

    if (entityUseAction == EnumWrappers.EntityUseAction.ATTACK) {
      meta.lastAttack = 0;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
    }
  )
  public void receiveRotationPacket(PacketEvent event) {
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
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION")
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    if (ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0)) {
      return;
    }
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();

    if (movementData.lastTeleport == 0) {
      return;
    }
    RotationSnapHeuristicMeta meta = metaOf(user);

    if(movementData.motionX() != 0 && movementData.motionZ() != 0) {
      meta.internalViolation -= 0.01;
      if(meta.internalViolation < 0)
        meta.internalViolation = 0;
    }

    double yawMotion = Math.abs(movementData.lastRotationYaw - movementData.rotationYaw);
    UserMetaAttackData attackData = user.meta().attackData();

    if ((yawMotion > 40 && meta.yawMotions[1] < 9) || (yawMotion > 25 && meta.yawMotions[1] == 0)) {
      if (meta.lastKeyStrafe != movementData.keyStrafe || meta.lastKeyForward != movementData.keyForward) {
        double directionLast = movementData.rotationYaw + keysToRotation(meta.lastKeyStrafe, meta.lastKeyForward);
        double direction = movementData.lastRotationYaw + keysToRotation(movementData.keyStrafe, movementData.keyForward);

        direction = floorModDouble(direction, 360);
        directionLast = floorModDouble(directionLast, 360);

//      String key = resolveKeysFromInput(movementData.keyForward, movementData.keyStrafe);
//      String lastKey = resolveKeysFromInput(meta.lastKeyForward, meta.lastKeyStrafe);
        boolean silentMovement = (int) (WrappedMathHelper.wrapAngleTo180_double(directionLast - direction) / 45d) == 0;
        if (movementData.keyForward != meta.lastKeyForward || movementData.keyStrafe != meta.lastKeyStrafe) {
          if (silentMovement && (movementData.keyForward != 0 || movementData.keyStrafe != 0) && (meta.lastKeyForward != 0 || meta.lastKeyStrafe != 0)) {
            meta.silentMovements[0] = KeyStates.SILENTMOVE;
          } else {
            meta.silentMovements[0] = KeyStates.CHANGED;
          }
        }
      }

      Tick tick = new Tick(
        meta.lastLastPosX,meta.lastLastPosY, meta.lastLastPosZ,
        movementData.lastRotationYaw, movementData.lastRotationPitch
      );
      meta.movementAtTick[0] = tick;

      for (Map.Entry<Integer, WrappedEntity> entry : user.meta().synchronizeData().synchronizedEntityMap().entrySet()) {
        WrappedEntity value = entry.getValue();
        if(value != null) {
          meta.entityPositions.put(entry.getKey(), value.positionHistory.get(Math.max(value.positionHistory.size() - 1, 0)));
        }
      }
    }

    boolean isSuspicious = (meta.yawMotions[1] == 0 && meta.yawMotions[0] > 25 && meta.yawMotions[0] > 9);

    boolean liteFlag = false;
    if(isSuspicious && meta.silentMovements[1] == KeyStates.SILENTMOVE && meta.rotationPacketCounter > 10 && movementData.lastTeleport > 7) {
      liteFlag = true;
    }

    isSuspicious = meta.yawMotions[1] < 9 && meta.yawMotions[0] > 40 && yawMotion < 9;

    if (isSuspicious && (meta.lastSwing <= 3 || meta.lastAttack <= 3) && meta.rotationPacketCounter > 10 && movementData.lastTeleport > 7) {
      double valueOfSnap = meta.yawMotions[0];
      String description = "rotation snap ["
        + MathHelper.formatDouble(meta.yawMotions[1], 2)
        + "/" + MathHelper.formatDouble(meta.yawMotions[0], 2)
        + "/" + MathHelper.formatDouble(yawMotion, 2) + "]"
        + " s:" + Math.min(meta.lastSwing, 9)
        + "/" + Math.min(meta.lastAttack, 9);

      if (meta.silentMovements[1] == KeyStates.SILENTMOVE) {
        description += " silent";
      } else if (meta.silentMovements[1] == KeyStates.CHANGED) {
        description += " changed";
      }

      boolean changedLookToEntity = false;
      if (attackData.lastAttackedEntity() != null && attackData.lastAttackedEntity().positionHistory.size() > 2) {
        WrappedEntity wrappedEntity = attackData.lastAttackedEntity();

        Tick tick = meta.movementAtTick[1];
        HashMap<Integer, WrappedEntity.EntityPositionContext> entityPositions = meta.entityPositions;
        WrappedEntity.EntityPositionContext lastEntityPosition = entityPositions.get(wrappedEntity.entityId());

        if(lastEntityPosition != null && tick != null) {
          WrappedAxisAlignedBB lastBoundingBox = WrappedEntity.entityBoundingBoxFrom(lastEntityPosition, wrappedEntity);
          Raytracer.EntityInteractionRaytrace last = distanceOf(
            player,
            lastBoundingBox,
            0,
            tick.posX, tick.posY, tick.posZ,
            tick.yaw, tick.pitch,
            0.1f,
            false
          );

          Raytracer.EntityInteractionRaytrace now = distanceOf(
            player,
            wrappedEntity.entityBoundingBox(),
            0,
            movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
            movementData.lastRotationYaw, movementData.lastRotationPitch,
            0.1f,
            false
          );

          changedLookToEntity = (last.reach != 10) != (now.reach != 10);
          if(changedLookToEntity) {
            description += " lookEn";
          }
        }
      }

      double vl = calculateViolation(valueOfSnap, changedLookToEntity, user, liteFlag);
      liteFlag = false;

      if (vl >= 40) {
        user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM);
      }
      if(vl > 70) {
        user.applyAttackNerfer(AttackNerfStrategy.CANCEL_FIRST_HIT);
      }

      Confidence confidence = Confidence.confidenceFrom((int) (vl + meta.internalViolation));
      meta.internalViolation += vl;

      if (confidence.level() >= 30) {
        meta.internalViolation -= confidence.level();
        description += " conf:" + confidence.level();

        if(user.meta().clientData().protocolVersion() > 47) {
          description += " " + user.meta().clientData().protocolVersion();
        }
        boolean isPartner = (UserMetaClientData.VERSION_DETAILS & 0x100) != 0;
        boolean isEnterprise = (UserMetaClientData.VERSION_DETAILS & 0x200) != 0;

        if(isPartner || isEnterprise) {
          Anomaly anomaly = Anomaly.anomalyOf("102", confidence, Anomaly.Type.KILLAURA, description, anomalieOptions(isPartner));
          parentCheck().saveAnomaly(player, anomaly);
        }
      }

      meta.entityPositions.clear();
    }

    if(liteFlag) {
      String description = "rotation snap scaffold [" +  MathHelper.formatDouble(meta.yawMotions[0], 2) + "]";

      boolean isPartner = (UserMetaClientData.VERSION_DETAILS & 0x100) != 0;
      boolean isEnterprise = (UserMetaClientData.VERSION_DETAILS & 0x200) != 0;

      if(isPartner || isEnterprise) {
        Anomaly anomaly = Anomaly.anomalyOf("103", Confidence.MAYBE, Anomaly.Type.KILLAURA, description, anomalieOptions(isPartner));
        parentCheck().saveAnomaly(player, anomaly);
      }
    }

    prepareNextTick(meta, yawMotion, user);
  }

  private static int anomalieOptions(boolean isPartner) {
    int options;
    if (IntaveControl.GOMME_MODE) {
      options = Anomaly.AnomalyOption.DELAY_32s;
    } else if (isPartner) {
      options = Anomaly.AnomalyOption.DELAY_64s;
    } else {
      options = Anomaly.AnomalyOption.DELAY_128s;
    }
    return options;
  }

  private double calculateViolation(double valueOfSnap, boolean channgedLookToEntity, User user, boolean liteFlag) {
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

    if(meta.lastBlockPlace < 3) {
      vl *= 1.5;
    }

    if (channgedLookToEntity) {
      vl *= 2;
    }

    if (meta.silentMovements[1] == KeyStates.SILENTMOVE) {
      vl *= 3;
    } else if (meta.silentMovements[1] == KeyStates.CHANGED) {
      vl *= 1.7;
    }

    if(user.meta().clientData().protocolVersion() <= UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE) {
      vl /= 3;
    }

    if(liteFlag) {
      vl += 10;
    }

    return Math.min(160, vl);
  }

  private void prepareNextTick(RotationSnapHeuristicMeta meta, double yawMotion, User user) {
    UserMetaMovementData movementData = user.meta().movementData();
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


  public static final class RotationSnapHeuristicMeta extends UserCustomCheckMeta {
    double lastLastPosX, lastLastPosY, lastLastPosZ;
    HashMap<Integer, WrappedEntity.EntityPositionContext> entityPositions = new HashMap<>();
    private Tick[] movementAtTick = new Tick[2];
    private double[] yawMotions = new double[2];
    private KeyStates[] silentMovements = new KeyStates[2];
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

  class Tick {
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
