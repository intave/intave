package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

@Reserved
public class ToolSwitchHeuristic extends MetaCheckPart<Heuristics, ToolSwitchHeuristic.ToolSwitchHeuristicMeta> {
  public ToolSwitchHeuristic(Heuristics parentCheck) {
    super(parentCheck, ToolSwitchHeuristicMeta.class);
  }

  @PacketSubscription(
      priority = ListenerPriority.HIGH,
      packetsIn = {
          POSITION, POSITION_LOOK, LOOK, FLYING, VEHICLE_MOVE
      }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    ToolSwitchHeuristicMeta meta = metaOf(player);
    meta.ticksSinceLastBreak++;
    meta.ticksSinceLastStop++;
  }

  @PacketSubscription(
      priority = ListenerPriority.HIGH,
      packetsIn = {
          PacketId.Client.BLOCK_DIG
      }
  )
  public void receiveBlockBreakAction(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().read(0);
    ToolSwitchHeuristicMeta meta = metaOf(player);

    // Update breaking state ticks
    if (digType == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK) {
      meta.ticksSinceLastBreak = 0;
    } else if (digType == EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK) {
      meta.ticksSinceLastStop = 0;
    }
  }

  @PacketSubscription(
      priority = ListenerPriority.HIGH,
      packetsIn = {
          PacketId.Client.HELD_ITEM_SLOT_IN
      }
  )
  public void receiveHeldItemSlotChange(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    User user = userOf(player);
    int currentSlot = user.meta().inventory().handSlot();
    Integer slot = packet.getIntegers().read(0);
    ToolSwitchHeuristicMeta meta = metaOf(player);

    // If a block break was recently started something is suspicious
    if (meta.ticksSinceLastBreak <= 1) {
      meta.suspiciousBreakStart = true;
      meta.lastSlot = currentSlot;
    }

    if (meta.suspiciousBreakStart && meta.ticksSinceLastStop <= 1 && meta.lastSlot == slot) {
      meta.suspiciousBreakStart = false;

      // Violate if buffer is too high
      if (++meta.vl > 3) {
        parentCheck().saveAnomaly(
            player,
            Anomaly.anomalyOf(
                "205",
                Confidence.LIKELY,
                Anomaly.Type.KILLAURA,
                "sent suspicious slot packets while breaking blocks (" + meta.ticksSinceLastStop + " ticks)"
            )
        );

        // Apply damage cancel if this happens too often
        if (++meta.cancelVl > 1) {
          user.nerf(AttackNerfStrategy.DMG_LIGHT, "205");
        }

        meta.vl = 0;
      }
    }
  }

  public static class ToolSwitchHeuristicMeta extends CheckCustomMetadata {
    public int ticksSinceLastBreak;
    public int ticksSinceLastStop;
    public int lastSlot;
    public boolean suspiciousBreakStart;
    public int vl;
    public int cancelVl;
  }
}
