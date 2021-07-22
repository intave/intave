package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.dispatch.AttackDispatcher;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.violation.AttackNerfStrategy;
import de.jpx3.intave.user.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.event.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.event.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.user.UserMetaClientData.VER_1_17;

public final class AttackReduceIgnoreHeuristic extends IntaveMetaCheckPart<Heuristics, AttackReduceIgnoreHeuristic.AttackReduceMeta> {
  private final IntavePlugin plugin;

  public AttackReduceIgnoreHeuristic(Heuristics parentCheck) {
    super(parentCheck, AttackReduceMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION, POSITION_LOOK
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    AttackReduceMeta heuristicMeta = metaOf(user);
    UserMetaClientData clientData = user.meta().clientData();

    if (clientData.protocolVersion() >= VER_1_17 || AttackDispatcher.REDUCING_DISABLED) {
      return;
    }

    if (movementData.recentlyEncounteredFlyingPacket(1)) {
      return;
    }

    ItemStack itemStack = inventoryData.heldItem();
    boolean knockbackEnchantment = itemStack != null && itemStack.containsEnchantment(Enchantment.KNOCKBACK);
    if (knockbackEnchantment) {
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
          //dmc4
          user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, "4");
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