package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
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
import de.jpx3.intave.tools.RotationMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaAttackData;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.entity.Player;

import java.util.List;

public final class PerfectAttackHeuristic extends IntaveMetaCheckPart<Heuristics, PerfectAttackHeuristic.PerfectAttackMeta> {
  private final IntavePlugin plugin;

  public PerfectAttackHeuristic(Heuristics parentCheck) {
    super(parentCheck, PerfectAttackMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "ARM_ANIMATION")
    }
  )
  public void evaluateFightAccuracy(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    UserMetaAttackData attackData = user.meta().attackData();
    PerfectAttackMeta heuristicMeta = metaOf(user);
    PacketType packetType = event.getPacketType();
    PacketContainer packet = event.getPacket();
    WrappedEntity attackedEntity = attackData.lastAttackedEntity();

    if (attackedEntity != null && !attackedEntity.moving(0.05)) {
      return;
    }
    if (!attackData.recentlyAttacked(500) || attackData.recentlySwitchedEntity(1000)) {
      return;
    }

    if (packetType == PacketType.Play.Client.ARM_ANIMATION) {
      heuristicMeta.swings++;
    } else {
      EnumWrappers.EntityUseAction action = packet.getEntityUseActions().read(0);
      if (action == EnumWrappers.EntityUseAction.ATTACK) {
        heuristicMeta.attacks++;
        heuristicMeta.swings--;
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK")
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    UserMetaAttackData attackData = user.meta().attackData();
    UserMetaMovementData movementData = user.meta().movementData();
    PerfectAttackMeta heuristicMeta = metaOf(user);

    if (!attackData.recentlyAttacked(1000) || attackData.recentlySwitchedEntity(500) || attackData.lastReach() < 1.0) {
      return;
    }

    float distanceToPerfectYaw = MathHelper.distanceInDegrees(attackData.perfectYaw(), movementData.rotationYaw);
    float yawSpeed = MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw);
    float pitchSpeed = MathHelper.distanceInDegrees(movementData.rotationPitch, movementData.lastRotationPitch);

    if (heuristicMeta.distanceToPerfectYawList.size() > 20) {
      double distanceAverage = RotationMathHelper.averageOf(heuristicMeta.distanceToPerfectYawList);
      double yawSpeedAverage = RotationMathHelper.averageOf(heuristicMeta.yawSpeedList);
      double failRate = (heuristicMeta.swings / heuristicMeta.attacks) * 100.0;

      if (failRate < 10 && (yawSpeedAverage > 10 || distanceAverage > 10)) {
        heuristicMeta.vl++;
        String description = "maintains high attack accuracy whilst aiming at hitbox corners " +
          "(fail:" + MathHelper.formatDouble(failRate, 2)
          + "%, r:" + MathHelper.formatDouble(yawSpeedAverage, 2)
          + ", d:" + MathHelper.formatDouble(distanceAverage, 2)
          + ") vl:" + MathHelper.formatDouble(heuristicMeta.vl, 2);
        int options = Anomaly.AnomalyOption.LIMIT_4 | Anomaly.AnomalyOption.SUGGEST_MINING | Anomaly.AnomalyOption.DELAY_16s;
        Anomaly anomaly = Anomaly.anomalyOf("51", Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(player, anomaly);
        user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM);
        if (heuristicMeta.vl >= 2) {
          user.applyAttackNerfer(AttackNerfStrategy.CANCEL_FIRST_HIT);
        }
      } else if (heuristicMeta.vl > 0) {
        heuristicMeta.vl -= 0.2;
      }

      heuristicMeta.attacks = 0;
      heuristicMeta.swings = 0;
      heuristicMeta.distanceToPerfectYawList.clear();
      heuristicMeta.yawSpeedList.clear();
    }

    if (yawSpeed > 5 && attackData.recentlyAttacked(60)) {
      heuristicMeta.distanceToPerfectYawList.add(distanceToPerfectYaw);
      heuristicMeta.yawSpeedList.add(yawSpeed + pitchSpeed);
    }
  }

  public final static class PerfectAttackMeta extends UserCustomCheckMeta {
    public double vl;
    public double attacks;
    public double swings;
    public final List<Float> distanceToPerfectYawList = Lists.newArrayList();
    public final List<Float> yawSpeedList = Lists.newArrayList();
  }
}