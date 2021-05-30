package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.violation.AttackNerfStrategy;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.entity.Player;

import static de.jpx3.intave.event.packet.PacketId.Client.*;

public final class NoSwingHeuristic extends IntaveMetaCheckPart<Heuristics, NoSwingHeuristic.NoSwingMeta> {

  public NoSwingHeuristic(Heuristics parentCheck) {
    super(parentCheck, NoSwingMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void entityHit(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    NoSwingMeta meta = metaOf(user);

    EnumWrappers.EntityUseAction entityUseAction = event.getPacket().getEntityUseActions().read(0);

    if (entityUseAction == EnumWrappers.EntityUseAction.ATTACK) {
//      if (meta.swingsThisTick == 0 && meta.attacksThisTick == 0
//        && user.meta().clientData().protocolVersion() == 47
//      ) {
//        event.setCancelled(true);
//      }
      meta.attacksThisTick++;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void swing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    NoSwingMeta meta = metaOf(user);

    meta.swingsThisTick++;
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    NoSwingMeta meta = metaOf(user);

    if (movementData.lastTeleport == 0) {
      return;
    }

    // fix?
    if (user.meta().clientData().clientVersionBehindServerVersion()) {
      return;
    }

    if (meta.attacksThisTick > 0) {
      if (meta.swingsThisTick == 0) {
        String details = "missing swing packet on attack";
        Anomaly anomaly = Anomaly.anomalyOf("171", /*Confidence.LIKELY*/Confidence.NONE, Anomaly.Type.KILLAURA, details, Anomaly.AnomalyOption.LIMIT_4);
        parentCheck().saveAnomaly(player, anomaly);
        //dmc26
        user.applyAttackNerfer(AttackNerfStrategy.CANCEL, "26");
      }
    }

    prepareNextTick(meta);
  }

  private void prepareNextTick(NoSwingMeta meta) {
    meta.swingsThisTick = 0;
    meta.attacksThisTick = 0;
  }

  public static class NoSwingMeta extends UserCustomCheckMeta {
    public int swingsThisTick;
    public int attacksThisTick;
  }
}
