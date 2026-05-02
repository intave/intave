package de.jpx3.intave.check.combat.heuristics.other;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.dispatch.AttackDispatcher;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_9;

public final class AttackReduceIgnoreHeuristic extends MetaCheckPart<Heuristics, AttackReduceIgnoreHeuristic.AttackReduceMeta> {
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
    MovementMetadata movementData = user.meta().movement();
    InventoryMetadata inventoryData = user.meta().inventory();
    AttackReduceMeta heuristicMeta = metaOf(user);
    AbilityMetadata abilities = user.meta().abilities();
    ProtocolMetadata clientData = user.meta().protocol();

    if (clientData.protocolVersion() >= VER_1_9 || AttackDispatcher.REDUCING_DISABLED) {
      return;
    }

    if (movementData.receivedFlyingPacketIn(1)) {
      return;
    }

    ItemStack itemStack = inventoryData.heldItem();
    boolean knockbackEnchantment = itemStack != null && itemStack.containsEnchantment(Enchantment.KNOCKBACK);
    boolean flying = abilities.probablyFlying() || abilities.allowFlying();

    if (knockbackEnchantment || flying || abilities.inGameModeIncludePending(AbilityTracker.GameMode.SPECTATOR)) {
      return;
    }

    if (movementData.lastSprinting && movementData.sprinting && movementData.pastPlayerReduceAttackPhysics == 0) {
      if (movementData.ignoredAttackReduce) {
        if (heuristicMeta.vl++ > 5) {
          String description = "did not reduce when attacking a player";
          // todo: is there even attack reduce on > 1.8???
          heuristicMeta.vl = 0;
        }
      } else if (heuristicMeta.vl > 0) {
        heuristicMeta.vl--;
      }
    }
  }

  public static final class AttackReduceMeta extends CheckCustomMetadata {
    private int vl;
  }
}