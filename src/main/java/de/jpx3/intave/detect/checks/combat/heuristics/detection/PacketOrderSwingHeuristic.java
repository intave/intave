package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

public final class PacketOrderSwingHeuristic extends IntaveMetaCheckPart<Heuristics, PacketOrderSwingHeuristic.PacketOrderSwingHeuristicMeta> {
  public PacketOrderSwingHeuristic(Heuristics parentCheck) {
    super(parentCheck, PacketOrderSwingHeuristicMeta.class);
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "ARM_ANIMATION")
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    PacketOrderSwingHeuristicMeta heuristicMeta = metaOf(player);
    heuristicMeta.swingTick = event.getPacketType() == PacketType.Play.Client.ARM_ANIMATION;
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY")
    }
  )
  public void receiveUseEntity(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaClientData clientData = user.meta().clientData();
    PacketOrderSwingHeuristicMeta heuristicMeta = metaOf(player);
    EnumWrappers.EntityUseAction action = event.getPacket().getEntityUseActions().read(0);
    if (clientData.flyingPacketStream() && action == EnumWrappers.EntityUseAction.ATTACK && !heuristicMeta.swingTick) {
      String description = "swing not correlated with attack";
      Anomaly anomaly = Anomaly.anomalyOf("31", Confidence.CERTAIN, Anomaly.Type.KILLAURA, description, Anomaly.AnomalyOption.DELAY_128s);
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  public final static class PacketOrderSwingHeuristicMeta extends UserCustomCheckMeta {
    private boolean swingTick;
  }
}
