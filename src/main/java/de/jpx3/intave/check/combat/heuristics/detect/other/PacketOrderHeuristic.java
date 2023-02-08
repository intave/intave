package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.Reserved;
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

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.DELAY_128s;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.LIMIT_2;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

@Reserved
public final class PacketOrderHeuristic extends MetaCheckPart<Heuristics, PacketOrderHeuristic.PacketOrderHeuristicMeta> {
  public PacketOrderHeuristic(Heuristics parentCheck) {
    super(parentCheck, PacketOrderHeuristicMeta.class);
  }

  @PacketSubscription(
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK,
      TRANSACTION,
      USE_ENTITY, BLOCK_PLACE, BLOCK_DIG
    }
  )
  public void receivePacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    PacketType type = event.getPacketType();
    PacketOrderHeuristicMeta meta = metaOf(player);

    boolean isMovement = type == PacketType.Play.Client.FLYING
      || type == PacketType.Play.Client.POSITION
      || type == PacketType.Play.Client.POSITION_LOOK
      || type == PacketType.Play.Client.LOOK;

    boolean isTransaction = type == PacketType.Play.Client.TRANSACTION;

    if (isMovement) {
      meta.movementSentThisTick = true;
      meta.betweenTransactionAndFlying.clear();
    } else if (isTransaction) {
      if (meta.movementSentThisTick && !meta.betweenTransactionAndFlying.isEmpty() && protocol.flyingPacketsAreSent()) {
        int options = DELAY_128s | LIMIT_2;
        String description = "invalid packet order (" + meta.betweenTransactionAndFlying.stream().map(PacketType::name).map(s -> s.replace("_", " ")).collect(Collectors.joining(", ")) + ")";
        Anomaly anomaly = Anomaly.anomalyOf("14", Confidence.NONE, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(player, anomaly);
        user.nerf(AttackNerfStrategy.BLOCKING, "31");
        user.nerf(AttackNerfStrategy.CRITICALS, "31");
      }
      meta.movementSentThisTick = false;
    } else if (meta.movementSentThisTick) {
      meta.betweenTransactionAndFlying.add(type);
    }
  }

  public static class PacketOrderHeuristicMeta extends CheckCustomMetadata {
    public boolean movementSentThisTick;
    public Set<PacketType> betweenTransactionAndFlying = new HashSet<>();
  }
}
