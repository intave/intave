package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class NoSwingHeuristic extends MetaCheckPart<Heuristics, NoSwingHeuristic.NoSwingMeta> {

  public NoSwingHeuristic(Heuristics parentCheck) {
    super(parentCheck, NoSwingMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void entityHit(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    NoSwingMeta meta = metaOf(user);

    PacketContainer packet = event.getPacket();
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
    if (action == null) {
      action = packet.getEnumEntityUseActions().read(0).getAction();
    }
    if (action == EnumWrappers.EntityUseAction.ATTACK) {
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
  public void swing(ProtocolPacketEvent event) {
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
  public void receiveMovementPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();
    NoSwingMeta meta = metaOf(user);

    if (movementData.lastTeleport == 0) {
      return;
    }

    // fix?
    if (user.meta().protocol().outdatedClient()) {
      return;
    }

    if (meta.attacksThisTick > 0) {
      if (meta.swingsThisTick == 0) {
        String details = "missing swing packet on attack";
        Anomaly anomaly = Anomaly.anomalyOf("171", /*Confidence.LIKELY*/Confidence.NONE, Anomaly.Type.KILLAURA, details, Anomaly.AnomalyOption.LIMIT_4);
        parentCheck().saveAnomaly(player, anomaly);
        //dmc26
        user.nerf(AttackNerfStrategy.CANCEL, "26");
      }
    }

    prepareNextTick(meta);
  }

  private void prepareNextTick(NoSwingMeta meta) {
    meta.swingsThisTick = 0;
    meta.attacksThisTick = 0;
  }

  public static class NoSwingMeta extends CheckCustomMetadata {
    public int swingsThisTick;
    public int attacksThisTick;
  }
}
