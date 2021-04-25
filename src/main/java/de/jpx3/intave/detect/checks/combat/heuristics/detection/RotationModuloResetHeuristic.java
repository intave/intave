package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.punishment.AttackCancelType;
import de.jpx3.intave.event.service.entity.WrappedEntity;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.client.RotationHelper;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.entity.Player;

public final class RotationModuloResetHeuristic extends IntaveMetaCheckPart<Heuristics, RotationModuloResetHeuristic.RotationModuloResetHeuristicMeta> {
  private final IntavePlugin plugin;

  public RotationModuloResetHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationModuloResetHeuristicMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK")
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    UserMetaAttackData attackData = user.meta().attackData();
    RotationModuloResetHeuristicMeta heuristicMeta = metaOf(user);

    heuristicMeta.rotationPacketCounter++;

    WrappedEntity attackedEntity = attackData.lastAttackedEntity();
    if (attackedEntity == null || attackData.recentlySwitchedEntity(5000) || movementData.lastTeleport < 100) {
      return;
    }

    float rotationYaw = movementData.rotationYaw;
    float lastRotationYaw = movementData.lastRotationYaw;

    /*
    1: Check stage
     */
    if (heuristicMeta.roundedRotationLooking) {
      if (entityInLineOfSight(user)) {
        float penaltyYaw = movementData.lastRotationYaw;
        if (penaltyYaw != 0) {
          String description = "possible rotation modulo clamp";
          int options = Anomaly.AnomalyOption.LIMIT_4 | Anomaly.AnomalyOption.DELAY_128s | Anomaly.AnomalyOption.SUGGEST_MINING;
          Anomaly anomaly = Anomaly.anomalyOf("101", Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
          plugin.eventService().attackCancelService().requestDamageCancel(user, AttackCancelType.MEDIUM);
        }
      }
      heuristicMeta.roundedRotationLooking = false;
      return;
    }

    /*
    2: Prepare for stage 1
     */
    if (attackData.recentlyAttacked(1000) && attackData.lastReach() > 1.0) {
      float receivedDistance = Math.abs(rotationYaw - lastRotationYaw);
      boolean roundingConditions = Math.abs(rotationYaw) <= 360 && Math.abs(lastRotationYaw) <= 360;
      boolean suspiciousYaw = roundingConditions && receivedDistance > 100;

      if (suspiciousYaw && entityInLineOfSight(user)) {
        heuristicMeta.roundedRotationLooking = true;
      }
    }
  }

  private boolean entityInLineOfSight(User user) {
    User.UserMeta meta = user.meta();
    UserMetaAttackData attackData = meta.attackData();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    boolean alternativePositionY = clientData.protocolVersion() == UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE;
    Raytracer.EntityInteractionRaytrace rayTraceResult = Raytracer.distanceOfIgnoringBlocks(
      user.player(),
      attackData.lastAttackedEntity(),
      alternativePositionY,
      movementData.lastPositionX,
      movementData.lastPositionY,
      movementData.lastPositionZ,
      movementData.rotationYaw,
      movementData.rotationPitch,
      0.1f
    );
    return rayTraceResult.reach != 10;
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
    RotationModuloResetHeuristicMeta meta = metaOf(user);

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
    RotationModuloResetHeuristicMeta meta = metaOf(user);

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
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION")
    }
  )
  public void receiveMovementPacket2(PacketEvent event) {
    if (ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.COMBAT_UPDATE)) {
      return;
    }
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    RotationModuloResetHeuristicMeta meta = metaOf(user);
    double yawMotion = Math.abs(movementData.lastRotationYaw - movementData.rotationYaw);

    if(yawMotion > 50) {
      UserMetaAttackData attackData = user.meta().attackData();
      if(attackData.lastAttackedEntity() != null) {
        WrappedEntity wrappedEntity = attackData.lastAttackedEntity();
        WrappedEntity.EntityPositionContext lastEntityPosition = wrappedEntity.positionHistory.get(Math.max(wrappedEntity.positionHistory.size() - 2, 0));
        float lastPerfectYaw = RotationHelper.resolveYawRotation(lastEntityPosition, movementData.lastPositionX, movementData.lastPositionZ);
        double lastDiff = Math.abs(WrappedMathHelper.wrapAngleTo180_double(lastPerfectYaw - movementData.lastRotationYaw));
        meta.perfectRotations[Math.floorMod(meta.index -1, meta.perfectRotations.length)] = lastDiff;

        double diff = Math.abs(WrappedMathHelper.wrapAngleTo180_double(attackData.perfectYaw() - movementData.rotationYaw));
        meta.perfectRotations[meta.index] = diff;
      }
    }

    if(meta.lastAttack > 3) {
      meta.perfectRotations[meta.index] = Double.POSITIVE_INFINITY;
    }

    boolean isLegit = false;

    for (int i = 0; i < meta.rotationMotions.length; i++) {
      double value = meta.rotationMotions[i];
      if (i == getHopIndex(meta)) {
        if (value < 50) {
          isLegit = true;
        }
      } else {
        if (value > 9) {
          isLegit = true;
        }
      }
    }

    if (yawMotion > 9) {
      isLegit = true;
    }

    if (!isLegit && (meta.lastSwing <= 3 || meta.lastAttack <= 3) && meta.rotationPacketCounter > 10 && movementData.lastTeleport > 7) {
      Confidence confidence = Confidence.NONE;
      String description = "rotation snap ("
        + getArrayAsString(meta.rotationMotions, yawMotion, meta.index)
        + " s:" + Math.min(meta.lastSwing, 99)
        + "/" + Math.min(meta.lastAttack, 99);

      double valueOfSnap = meta.rotationMotions[getHopIndex(meta)];
      if(valueOfSnap > 90 && meta.lastAttack <= 3) {
        confidence = Confidence.COULDBE;
      }

      UserMetaAttackData attackData = user.meta().attackData();
      if(attackData.lastAttackedEntity() != null) {
        double values[] = new double[] {
          meta.perfectRotations[Math.floorMod(getHopIndex(meta) - 1, meta.perfectRotations.length)],
          meta.perfectRotations[Math.floorMod(getHopIndex(meta), meta.perfectRotations.length)]};

        if(values[1] == Double.POSITIVE_INFINITY) {
          values[1] = Math.abs(WrappedMathHelper.wrapAngleTo180_double(attackData.perfectYaw() - movementData.rotationYaw));
        }

        if(values[0] != Double.POSITIVE_INFINITY && values[1] != Double.POSITIVE_INFINITY)
        {
          double minValue = Math.min(values[0], values[1]);
          double maxValue = Math.max(values[0], values[1]);

          if(minValue < 10 && maxValue > 50) {
            if(valueOfSnap > 360) {
              confidence = Confidence.LIKELY;
            } else if(valueOfSnap > 80) {
              confidence = Confidence.PROBABLE;
            } else {
              confidence = Confidence.MAYBE;
            }
            description += " pYaw:" + MathHelper.formatDouble(minValue, 2)
              + "/" + MathHelper.formatDouble(maxValue, 2);
          }
        }
      }

      if(valueOfSnap >= 178 && confidence.level() < Confidence.LIKELY.level()) {
        confidence = Confidence.PROBABLE;
      }

      description += " con:" + confidence.level();

      if(confidence.level() != 0) {
        int options = Anomaly.AnomalyOption.DELAY_128s;
        Anomaly anomaly = Anomaly.anomalyOf("102", confidence, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(player, anomaly);
      }
    }

    prepareNextTick(meta, yawMotion);
  }

  private String getArrayAsString(double[] array, double additional, int index) {
    String out = "[";
    for(int i = 0; i < array.length; i++) {
      double value = array[Math.floorMod(i + index - 3, array.length)];
      out += MathHelper.formatDouble(value, 2) + "/";
    }
    out += MathHelper.formatDouble(additional, 2);
    out += "]";
    return out;
  }

  private int getHopIndex(RotationModuloResetHeuristicMeta meta) {
    return Math.floorMod(meta.index , meta.rotationMotions.length);
  }

  private void prepareNextTick(RotationModuloResetHeuristicMeta meta, double yawMotion) {
    meta.lastSwing++;
    meta.lastAttack++;

    meta.rotationMotions[meta.index] = yawMotion;
    meta.index++;
    if (meta.index > meta.rotationMotions.length - 1) {
      meta.index = 0;
    }
  }


  public static final class RotationModuloResetHeuristicMeta extends UserCustomCheckMeta {
    // used to disable the check on startup
    private int rotationPacketCounter;
    private double[] rotationMotions = new double[2];
    private double[] perfectRotations = new double[2];
    private int index;
    private int lastSwing;
    private int lastAttack;
    private boolean roundedRotationLooking;
  }
}