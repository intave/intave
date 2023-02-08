package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
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
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    PacketOrderSwingHeuristicMeta heuristicMeta = metaOf(player);
    heuristicMeta.swingTick = event.getPacketType() == PacketType.Play.Client.ARM_ANIMATION;
  }

  @PacketSubscription(
    packetsIn = {
      USE_ENTITY
    }
  )
  public void receiveUseEntity(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    PacketOrderSwingHeuristicMeta heuristicMeta = metaOf(player);
    PacketContainer packet = event.getPacket();
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
    if (action == null) {
      action = packet.getEnumEntityUseActions().read(0).getAction();
    }
    if (user.meta().abilities().ignoringMovementPackets()) {
      return;
    }
    if (clientData.flyingPacketsAreSent() && action == EnumWrappers.EntityUseAction.ATTACK && !heuristicMeta.swingTick) {
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
