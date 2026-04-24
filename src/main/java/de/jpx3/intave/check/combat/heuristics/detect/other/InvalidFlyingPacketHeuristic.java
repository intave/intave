package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public class InvalidFlyingPacketHeuristic extends MetaCheckPart<Heuristics, InvalidFlyingPacketHeuristic.InvalidFlyingPacketHeuristicMeta> {

  public InvalidFlyingPacketHeuristic(Heuristics parentCheck) {
    super(parentCheck, InvalidFlyingPacketHeuristic.InvalidFlyingPacketHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION, LOOK, POSITION_LOOK, FLYING
    }
  )
  public void receiveFlyingPacket(ProtocolPacketEvent event, WrapperPlayClientPlayerFlying packet) {
    Player player = event.getPlayer();
    User user = userOf(player);
    InvalidFlyingPacketHeuristicMeta meta = metaOf(user);
    // Only execute check on 1.9+
    if (!user.meta().protocol().flyingPacketsAreSent()) {
      boolean groundState = packet.isOnGround();
      //noinspection deprecation
      if (event.getPacketType() == PacketType.Play.Client.PLAYER_FLYING) {
        if (groundState == meta.previousGroundState) {
          meta.threshold++;
          // Threshold to not spam violations
          if (meta.threshold > 20) {
            Anomaly anomaly = Anomaly.anomalyOf("500",
                Confidence.NONE,
                Anomaly.Type.KILLAURA,
                "sent invalid flying packets",
                Anomaly.AnomalyOption.DELAY_16s);
            parentCheck().saveAnomaly(player, anomaly);
            meta.threshold = 0;
          }
        }
      }
      meta.previousGroundState = groundState;
    }
  }

  public static class InvalidFlyingPacketHeuristicMeta extends CheckCustomMetadata {
    public boolean previousGroundState;
    public int threshold;
  }
}
