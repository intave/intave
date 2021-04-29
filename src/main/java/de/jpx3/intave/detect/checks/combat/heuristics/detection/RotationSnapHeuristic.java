package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
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
import de.jpx3.intave.tools.client.RotationHelper;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.entity.Player;

import static de.jpx3.intave.world.raytrace.Raytracer.distanceOf;

public class RotationSnapHeuristic extends IntaveMetaCheckPart<Heuristics, RotationSnapHeuristic.RotationSnapHeuristicMeta> {
  private final IntavePlugin plugin;

  public RotationSnapHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationSnapHeuristicMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
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

    if(movementData.lastTeleport == 0) {
      return;
    }

    RotationSnapHeuristicMeta meta = metaOf(user);
    double yawMotion = Math.abs(movementData.lastRotationYaw - movementData.rotationYaw);
    UserMetaAttackData attackData = user.meta().attackData();
    double diffPerfectYaw = Math.abs(WrappedMathHelper.wrapAngleTo180_double(attackData.perfectYaw() - movementData.rotationYaw));

    if(yawMotion > 40 && meta.yawMotions[1] < 9) {
      if(meta.lastKeyStrafe != movementData.keyStrafe || meta.lastKeyForward != movementData.keyForward) {
        double directionLast = movementData.rotationYaw + keysToRotation(meta.lastKeyStrafe, meta.lastKeyForward);
        double direction = movementData.lastRotationYaw + keysToRotation(movementData.keyStrafe, movementData.keyForward);

        direction = Math.floorMod((int) direction, 360);
        directionLast = Math.floorMod((int) directionLast, 360);

//      String key = resolveKeysFromInput(movementData.keyForward, movementData.keyStrafe);
//      String lastKey = resolveKeysFromInput(meta.lastKeyForward, meta.lastKeyStrafe);
        boolean silentMovement = (int) (WrappedMathHelper.wrapAngleTo180_double(directionLast - direction) / 45d) == 0;
        if(silentMovement && (movementData.keyForward != 0 || movementData.keyStrafe != 0) && (meta.lastKeyForward != 0 || meta.lastKeyStrafe != 0)) {
          meta.silentMovements[0] = KeyStates.SILENTMOVE;
        } else {
          meta.silentMovements[0] = KeyStates.CHANGED;
        }
      }

      if(attackData.lastAttackedEntity() != null) {
        WrappedEntity wrappedEntity = attackData.lastAttackedEntity();
        WrappedEntity.EntityPositionContext lastEntityPosition = wrappedEntity.positionHistory.get(Math.max(wrappedEntity.positionHistory.size() - 2, 0));
        float lastPerfectYaw = RotationHelper.resolveYawRotation(lastEntityPosition, movementData.lastPositionX, movementData.lastPositionZ);
        double lastDiff = Math.abs(WrappedMathHelper.wrapAngleTo180_double(lastPerfectYaw - movementData.lastRotationYaw));
        meta.perfectRotations[1] = lastDiff;

        meta.perfectRotations[0] = diffPerfectYaw;
      }
    }

    boolean isLegit = meta.yawMotions[1] > 9 || meta.yawMotions[0] < 40 || yawMotion > 9;

    if (!isLegit && (meta.lastSwing <= 3 || meta.lastAttack <= 3) && meta.rotationPacketCounter > 20 && movementData.lastTeleport > 7) {
      double valueOfSnap = meta.yawMotions[0];
      String description = "rotation snap ["
        +  MathHelper.formatDouble(meta.yawMotions[1], 2)
        + "/" +  MathHelper.formatDouble(meta.yawMotions[0], 2)
        + "/" + MathHelper.formatDouble(yawMotion, 2) + "]"
        + " s:" + Math.min(meta.lastSwing, 9)
        + "/" + Math.min(meta.lastAttack, 9);

      double addVL;
      if(valueOfSnap > 90 && meta.lastAttack <= 3) {
        addVL = 20;
      } else  if(valueOfSnap > 55) {
        addVL = 15;
      } else {
        addVL = 10;
      }

      if(attackData.lastAttackedEntity() != null) {
        double minValue = Math.min(meta.perfectRotations[0], meta.perfectRotations[1]);
        double maxValue = Math.max(meta.perfectRotations[0], meta.perfectRotations[1]);

        if(maxValue == Double.POSITIVE_INFINITY) {
          minValue = Math.min(meta.perfectRotations[1], meta.perfectRotations[2]);
          maxValue = Math.max(meta.perfectRotations[1], meta.perfectRotations[2]);
        }

        if(maxValue != Double.POSITIVE_INFINITY) {
          if(minValue < 10 && maxValue > 40) {
            if(valueOfSnap > 360) {
              addVL = 40;
            } else if(valueOfSnap > 50) {
              addVL = 20;
            } else {
              addVL = 10;
            }
            description += " pYaw:"
              + MathHelper.formatDouble(minValue, 2)
              + "/" + MathHelper.formatDouble(maxValue, 2);
          }
        }
      }

      if(valueOfSnap >= 178) {
        addVL *= 2;
      }

      switch (meta.silentMovements[1]) {
        case CHANGED:
          addVL *= 1.5;
          description += " changed";
          break;
        case SILENTMOVE:
          if(valueOfSnap > 90) {
            addVL *= 3;
          } else {
            addVL *= 2;
          }
          description += " silent";
          break;
        default:
          break;
      }

      if(addVL >= 40) {
        user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM);
      }
      Confidence confidence = Confidence.confidenceFrom((int) (addVL + meta.internalViolation));
      meta.internalViolation += addVL;
      meta.internalViolation -= confidence.level();
      description += " conf:" + confidence.level();

//      player.sendMessage("" + addVL);
      if(addVL > 10) {
        boolean isPartner = (UserMetaClientData.VERSION_DETAILS & 0x100) != 0;
        boolean isEnterprise = (UserMetaClientData.VERSION_DETAILS & 0x200) != 0;

        int options = isPartner ? Anomaly.AnomalyOption.DELAY_64s : Anomaly.AnomalyOption.DELAY_128s;
        Anomaly anomaly = Anomaly.anomalyOf("102", confidence, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(player, anomaly);
      }
    }

    prepareNextTick(meta, yawMotion, user);
  }

  private boolean entityInLineOfSight(User user, float yaw, float pitch, double posX, double posY, double posZ) {
    Player player = user.player();
    UserMetaAttackData attackData = user.meta().attackData();
    WrappedEntity entity = attackData.lastAttackedEntity();
    float expandHitbox = 0.1f;

    // mouse delay fix
    Raytracer.EntityInteractionRaytrace distanceOfResult = distanceOf(
      player,
      entity, false,
      posX, posY, posZ,
      yaw, pitch,
      expandHitbox
    );

    return distanceOfResult.reach != 10;
  }

  private void prepareNextTick(RotationSnapHeuristicMeta meta, double yawMotion, User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    meta.lastKeyForward = movementData.keyForward;
    meta.lastKeyStrafe = movementData.keyStrafe;

    meta.yawMotions[1] = meta.yawMotions[0];
    meta.yawMotions[0] = yawMotion;

    meta.perfectRotations[2] = meta.perfectRotations[1];
    meta.perfectRotations[1] = meta.perfectRotations[0];
    meta.perfectRotations[0] = Double.POSITIVE_INFINITY;

    meta.silentMovements[1] = meta.silentMovements[0];
    meta.silentMovements[0] = KeyStates.NONE;

    meta.lastSwing++;
    meta.lastAttack++;
  }


  public static final class RotationSnapHeuristicMeta extends UserCustomCheckMeta {
    private double[] yawMotions = new double[2];
    private double[] perfectRotations = new double[3];
    private KeyStates[] silentMovements = new KeyStates[2];
    private int internalViolation;
    private int lastKeyForward;
    private int lastKeyStrafe;
    // used to disable the check on startup
    private int rotationPacketCounter;
    private int lastSwing;
    private int lastAttack;
  }

  enum KeyStates {
    NONE, CHANGED, SILENTMOVE
  }
}
