package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.math.Histogram;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.converter.PlayerAction;
import de.jpx3.intave.packet.converter.PlayerActionResolver;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.DELAY_16s;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.LIMIT_1;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class SprintOnAttackHeuristic extends MetaCheckPart<Heuristics, SprintOnAttackHeuristic.SprintOnAttackHeuristicMeta> {

  public SprintOnAttackHeuristic(Heuristics parentCheck) {
    super(parentCheck, SprintOnAttackHeuristic.SprintOnAttackHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ENTITY_ACTION_IN
    }
  )
  public void receiveEntityActionPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    SprintOnAttackHeuristicMeta meta = metaOf(user);
    PlayerAction action = PlayerActionResolver.resolveActionFromPacket(event.getPacket());

    if (action == PlayerAction.START_SPRINTING) {
      meta.startSprint = true;
      if (meta.lastSprintStopAttackPastTicks < 10 && user.meta().attack().attackPastTicks < 10) {
        int difference = Math.abs(user.meta().attack().attackPastTicks - meta.lastSprintStopAttackPastTicks);
        Histogram resprintDelayHistogram = meta.resprintDelayHistogram;
        double var = resprintDelayHistogram.variance();
        if (difference == 1) {
          if (meta.continouslyInstantResprintVL++ > 8) {
//            player.sendMessage(ChatColor.RED + "Instant resprint detected");
            user.nerfPermanently(AttackNerfStrategy.APPLY_LESS_KNOCKBACK, "SOA");
            if (meta.continouslyInstantResprintVL > 12) {
              user.nerfPermanently(AttackNerfStrategy.DMG_LIGHT, "SOA");
              user.nerfPermanently(AttackNerfStrategy.CRITICALS, "SOA");
            }
          }
        } else {
          meta.continouslyInstantResprintVL = 0;
        }
        if (difference < 4) {
          resprintDelayHistogram.add(difference);
        } else {
          resprintDelayHistogram.add(5);
        }
        Predicate<Double> binFilter = aDouble -> aDouble >= 0 && aDouble < 3.5;
        double criticalVar = resprintDelayHistogram.variance(binFilter);
        double uniformMse = resprintDelayHistogram.mseUniform(binFilter);

        if (resprintDelayHistogram.size() > 40 && uniformMse < 3.5) {
          //player.sendMessage(ChatColor.RED + "Too uniform resprint delay  " + uniformMse + " " + Arrays.toString(resprintDelayHistogram.bins()));
//          user.nerfPermanently(AttackNerfStrategy.APPLY_LESS_KNOCKBACK, "SOA1");
          if (uniformMse < 2) {
            user.nerfPermanently(AttackNerfStrategy.DMG_LIGHT, "SOA1");
          }
        }

        if (resprintDelayHistogram.size() > 30 && (var < 0.225 && criticalVar < 0.225)) {
          //player.sendMessage(ChatColor.RED + "Low variance resprint delay " + var + " " + criticalVar + " " + Arrays.toString(resprintDelayHistogram.bins()));
//          user.nerfPermanently(AttackNerfStrategy.APPLY_LESS_KNOCKBACK, "SOA2");
          if (var < 0.1 && criticalVar < 0.1) {
            user.nerfPermanently(AttackNerfStrategy.DMG_LIGHT, "SOA2");
          }
        } else if (resprintDelayHistogram.size() > 10 && (var < 0.1 && criticalVar < 0.1)) {
          //player.sendMessage(ChatColor.RED + "Low variance resprint delay 2" + var + " " + criticalVar + " " + Arrays.toString(resprintDelayHistogram.bins()));
//          user.nerfPermanently(AttackNerfStrategy.APPLY_LESS_KNOCKBACK, "SOA3");
        }
      }
    } else if (action == PlayerAction.STOP_SPRINTING) {
      meta.stopSprint = true;
      meta.lastSprintStopAttackPastTicks = user.meta().attack().attackPastTicks;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void receiveAttackPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    SprintOnAttackHeuristicMeta meta = metaOf(user);
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
      POSITION, POSITION_LOOK, LOOK, FLYING
    }
  )
  public void receiveMovePacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    SprintOnAttackHeuristicMeta meta = metaOf(user);
    MovementMetadata movementData = user.meta().movement();

    if (movementData.lastTeleport == 0) {
      return;
    }

    if (meta.lastAttack == 0) {
      meta.totalAttacks++;
      if (meta.startSprint) {
        meta.attacksWithSprintChangeBefore++;
      }
    }

    if (meta.totalAttacks > 10) {
      double ratioBefore = (double) meta.attacksWithSprintChangeBefore / (double) meta.totalAttacks;
      if (ratioBefore > 0.9) {
        Anomaly anomaly = Anomaly.anomalyOf(
          "200",
          Confidence.NONE,
          Anomaly.Type.KILLAURA,
          "sprint-toggles aligned with attacks (" + MathHelper.formatDouble(ratioBefore, 2) + "%)",
          DELAY_16s | LIMIT_1
        );
        parentCheck().saveAnomaly(player, anomaly);
      }

      meta.totalAttacks = 0;
      meta.attacksWithSprintChangeBefore = 0;
    }

    prepareNextTick(meta);
  }

  private void prepareNextTick(SprintOnAttackHeuristicMeta meta) {
    meta.lastAttack++;
    meta.startSprint = false;
    meta.stopSprint = false;
  }

  public static class SprintOnAttackHeuristicMeta extends CheckCustomMetadata {
    private int attacksWithSprintChangeBefore;
    private int attacksWithSprintChangeAfter;
    private int totalAttacks;
    private boolean startSprint;
    private boolean stopSprint;
    private int lastAttack;

    private int lastSprintStopAttackPastTicks;
    private int continouslyInstantResprintVL;
    private final Histogram resprintDelayHistogram = new Histogram(1d, 5d, 1d, 100);
  }
}