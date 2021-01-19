package de.jpx3.intave.detect.checks.combat.heuristics;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.entity.Player;

public final class PacketSprintToggleHeuristic extends IntaveMetaCheckPart<Heuristics, PacketSprintToggleHeuristic.PacketSprintToggleHeuristicMeta> {
  public PacketSprintToggleHeuristic(Heuristics parentCheck) {
    super(parentCheck, PacketSprintToggleHeuristicMeta.class);
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK")
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    PacketSprintToggleHeuristicMeta heuristicMeta = metaOf(player);
    heuristicMeta.sprintTogglesInTick = 0;
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "ENTITY_ACTION")
    }
  )
  public void receiveEntityAction(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    PacketSprintToggleHeuristicMeta heuristicMeta = metaOf(user);

    if (heuristicMeta.sprintTogglesInTick++ >= 1) {
      boolean flyingPacketStream = clientData.flyingPacketStream();
      boolean checkable = flyingPacketStream || movementData.pastFlyingPacketAccurate > 2;
      if (checkable) {
        String description = "sent too many sprint toggles per tick (" + heuristicMeta.sprintTogglesInTick + ")";
        if (!flyingPacketStream) {
          description += " (unsafe)";
        }
        // could be CERTAIN
        Confidence confidence = flyingPacketStream ? Confidence.PROBABLE : Confidence.MAYBE;
        int options = Anomaly.AnomalyOption.DELAY_128s | Anomaly.AnomalyOption.REQUIRES_HEAVY_COMBAT;
        Anomaly anomaly = Anomaly.anomalyOf(confidence, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(player, anomaly);
      }
    }
  }

  public static final class PacketSprintToggleHeuristicMeta extends UserCustomCheckMeta {
    private int sprintTogglesInTick;
  }
}