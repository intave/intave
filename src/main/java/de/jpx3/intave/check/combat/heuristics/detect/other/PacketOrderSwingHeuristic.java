package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class PacketOrderSwingHeuristic extends MetaCheckPart<Heuristics, PacketOrderSwingHeuristic.PacketOrderSwingHeuristicMeta> {
  private final IntavePlugin plugin;

  public PacketOrderSwingHeuristic(Heuristics parentCheck) {
    super(parentCheck, PacketOrderSwingHeuristicMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK, ARM_ANIMATION
    }
  )
  public void receiveMovementPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    PacketOrderSwingHeuristicMeta heuristicMeta = metaOf(player);
    heuristicMeta.swingTick = event.getPacketType() == PacketType.Play.Client.ANIMATION;
  }

  @PacketSubscription(
    packetsIn = {
      USE_ENTITY
    }
  )
  public void receiveUseEntity(ProtocolPacketEvent event, WrapperPlayClientInteractEntity packet) {
    Player player = event.getPlayer();
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    PacketOrderSwingHeuristicMeta heuristicMeta = metaOf(player);
    if (user.meta().abilities().ignoringMovementPackets()) {
      return;
    }
    if (clientData.flyingPacketsAreSent() && packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK && !heuristicMeta.swingTick) {
      String description = "swing not correlated with attack (" + user.meta().protocol().versionString() + ")";
      Anomaly anomaly = Anomaly.anomalyOf("31", Confidence.LIKELY, Anomaly.Type.KILLAURA, description);
      parentCheck().saveAnomaly(player, anomaly);
      //dmc11
      user.nerf(AttackNerfStrategy.DMG_LIGHT, "11");
    }
  }

  public static final class PacketOrderSwingHeuristicMeta extends CheckCustomMetadata {
    private boolean swingTick;
  }
}
