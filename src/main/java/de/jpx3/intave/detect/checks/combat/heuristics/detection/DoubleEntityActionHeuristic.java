package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.detect.MetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.reflect.converters.PlayerAction;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.reflect.converters.PlayerActionResolver.resolveActionFromPacket;

public final class DoubleEntityActionHeuristic extends MetaCheckPart<Heuristics, DoubleEntityActionHeuristic.DoubleEntityActionHeuristicMeta> {

  public DoubleEntityActionHeuristic(Heuristics parentCheck) {
    super(parentCheck, DoubleEntityActionHeuristic.DoubleEntityActionHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ENTITY_ACTION
    }
  )
  public void receiveEntityActionPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();
    PlayerAction action = resolveActionFromPacket(event.getPacket());

    String message = null;
    if(action == PlayerAction.START_SNEAKING) {
      if(movementData.sneaking) {
        message = "sent start_sneak packet twice";
      }
    }
    if(action == PlayerAction.STOP_SNEAKING) {
      if(!movementData.sneaking) {
        message = "sent stop_sneak packet twice";
      }
    }
    if(action == PlayerAction.START_SPRINTING) {
      if(movementData.sprinting) {
        message = "sent start_sprint packet twice";
      }
    }
    if(action == PlayerAction.STOP_SPRINTING) {
      if(!movementData.sprinting) {
        message = "sent stop_sprint packet twice";
      }
    }

    DoubleEntityActionHeuristicMeta meta = metaOf(user);
    if(message != null && meta.ticksSinceJoin > 10) {
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
    int ticksSinceJoin = 0;
  }
}