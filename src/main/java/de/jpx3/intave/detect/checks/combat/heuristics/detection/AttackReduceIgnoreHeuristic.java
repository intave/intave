package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.dispatch.AttackDispatcher;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.punishment.AttackNerfStrategy;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

public final class AttackReduceIgnoreHeuristic extends IntaveMetaCheckPart<Heuristics, AttackReduceIgnoreHeuristic.AttackReduceMeta> {
  private final IntavePlugin plugin;

  public AttackReduceIgnoreHeuristic(Heuristics parentCheck) {
    super(parentCheck, AttackReduceMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK")
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    AttackReduceMeta heuristicMeta = metaOf(user);

    if (AttackDispatcher.REDUCING_DISABLED) {
      return;
    }

    if (movementData.recentlyEncounteredFlyingPacket(1)) {
      return;
    }

    if (movementData.lastSprinting && movementData.sprinting && movementData.pastPlayerAttackPhysics == 0) {
      if (movementData.ignoredAttackReduce) {
        if (heuristicMeta.vl++ > 5) {
          String description = "did not reduce when attacking a player";
          int options = Anomaly.AnomalyOption.LIMIT_2 | Anomaly.AnomalyOption.SUGGEST_MINING | Anomaly.AnomalyOption.DELAY_16s;
          Anomaly anomaly = Anomaly.anomalyOf("21", Confidence.LIKELY, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
          heuristicMeta.vl = 0;
          user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM);
        }
      } else if (heuristicMeta.vl > 0) {
        heuristicMeta.vl--;
      }
    }
  }

  public static final class AttackReduceMeta extends UserCustomCheckMeta {
    private int vl;
  }
}