package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.converter.PlayerAction;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.packet.converter.PlayerActionResolver.resolveActionFromPacket;

public final class DoubleEntityActionHeuristic extends MetaCheckPart<Heuristics, DoubleEntityActionHeuristic.DoubleEntityActionHeuristicMeta> {

  public DoubleEntityActionHeuristic(Heuristics parentCheck) {
    super(parentCheck, DoubleEntityActionHeuristic.DoubleEntityActionHeuristicMeta.class);
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
    MovementMetadata movementData = user.meta().movement();
    PlayerAction action = resolveActionFromPacket(event.getPacket());
    ProtocolMetadata protocolMetadata = user.meta().protocol();
    DoubleEntityActionHeuristicMeta meta = metaOf(user);

    String message = null;
    if (action == PlayerAction.START_SNEAKING) {
      if (meta.isSneaking != null && meta.isSneaking) {
        message = "sent start_sneak packet twice";
      }
      meta.isSneaking = true;
    }
    if (action == PlayerAction.STOP_SNEAKING) {
      if (meta.isSneaking != null && !meta.isSneaking) {
        message = "sent stop_sneak packet twice";
      }
      meta.isSneaking = false;
    }
    if (action == PlayerAction.START_SPRINTING) {
      if (meta.isSprinting != null && meta.isSprinting) {
        message = "sent start_sprint packet twice";
      }
      meta.isSprinting = true;
    }
    if (action == PlayerAction.STOP_SPRINTING) {
      if (meta.isSprinting != null && !meta.isSprinting) {
        message = "sent stop_sprint packet twice";
      }
      meta.isSprinting = false;
    }

    if (message != null && meta.ticksSinceJoin > 10) {
      message += " " + protocolMetadata.protocolVersion();
      // Be careful before setting a confidence because it false flags when reloading the server
      Anomaly anomaly = Anomaly.anomalyOf("190",
        Confidence.NONE,
        Anomaly.Type.KILLAURA,
        message, Anomaly.AnomalyOption.DELAY_16s
      );
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  @PacketSubscription(
    packetsIn = {
      POSITION, POSITION_LOOK
    }
  )
  public void receivePositonPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    DoubleEntityActionHeuristicMeta meta = metaOf(user);

    meta.ticksSinceJoin++;
  }

  public static class DoubleEntityActionHeuristicMeta extends CheckCustomMetadata {
    private Boolean isSprinting = null;
    private Boolean isSneaking = null;
    int ticksSinceJoin = 0;
  }
}