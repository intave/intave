package de.jpx3.intave.check.combat.heuristics.detect.combatpatterns;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ARM_ANIMATION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

public final class LongTermClickAccuracyHeuristic extends MetaCheckPart<Heuristics, LongTermClickAccuracyHeuristic.ClickAccuracyMeta> {
  public LongTermClickAccuracyHeuristic(Heuristics parentCheck) {
    super(parentCheck, ClickAccuracyMeta.class);
  }

  @PacketSubscription(
    packetsIn = {
      USE_ENTITY, ARM_ANIMATION
    }
  )
  public void evaluateFightAccuracy(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    AttackMetadata attackData = user.meta().attack();
    ClickAccuracyMeta heuristicMeta = metaOf(user);
    PacketTypeCommon packetType = event.getPacketType();
    Entity entity = attackData.lastAttackedEntity();
    if (entity == null || !entity.moving(0.05) || entity.ticksAlive < 200) {
      return;
    }
    if (!attackData.recentlyAttacked(500) || attackData.recentlySwitchedEntity(1000)) {
      return;
    }
    if (packetType == PacketType.Play.Client.ANIMATION) {
      heuristicMeta.swings++;
    } else {
      WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity((com.github.retrooper.packetevents.event.PacketReceiveEvent) event);
      if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
        heuristicMeta.attacks++;
        heuristicMeta.swings--;
        double failRate = (heuristicMeta.swings / heuristicMeta.attacks) * 100.0;
//        Synchronizer.synchronize(() -> player.sendMessage(String.valueOf(failRate)));
        if (heuristicMeta.attacks > 80) {
          if (failRate >= 0 && failRate < 3) {
            Anomaly anomaly = Anomaly.anomalyOf("210", Confidence.NONE, Anomaly.Type.KILLAURA, "player maintains high attack accuracy (failRate: " + MathHelper.formatDouble(failRate, 2) + "%)");
            parentCheck().saveAnomaly(player, anomaly);
          }
          heuristicMeta.attacks = 0;
          heuristicMeta.swings = 0;
        }
      }
    }
  }

  public static class ClickAccuracyMeta extends CheckCustomMetadata {
    public double attacks;
    public double swings;
  }
}
