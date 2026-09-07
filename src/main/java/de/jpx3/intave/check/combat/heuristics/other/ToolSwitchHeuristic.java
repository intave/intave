/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.check.combat.heuristics.other;

import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.view.BlockPositionView;
import de.jpx3.intave.packet.view.PacketEventsBlockPositionView;
import de.jpx3.intave.packet.view.ProtocolLibBlockPositionView;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public class ToolSwitchHeuristic extends ClassicHeuristic<ToolSwitchHeuristic.ToolSwitchHeuristicMeta> {
  public ToolSwitchHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.TOOL_SWITCH, ToolSwitchHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION, POSITION_LOOK, LOOK, FLYING, VEHICLE_MOVE
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    handleMovementPacket(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION, POSITION_LOOK, LOOK, FLYING, VEHICLE_MOVE
    }
  )
  public void receiveMovementPacket(Player player) {
    if (player == null) {
      return;
    }
    handleMovementPacket(player);
  }

  /** Engine independent handling; the body only advances tick counters, it never reads the packet. */
  private void handleMovementPacket(Player player) {
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
    handleBlockBreakAction(new ProtocolLibBlockPositionView(event));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      PacketId.Client.BLOCK_DIG
    }
  )
  public void receiveBlockBreakAction(PacketReceiveEvent event) {
    PacketEventsBlockPositionView view = PacketEventsBlockPositionView.of(event);
    if (view == null) {
      return;
    }
    handleBlockBreakAction(view);
  }

  /**
   * Engine independent handling; see {@link BlockPositionView}. The only thing read off the packet
   * is the dig action, which both engines report through the same neutral enum.
   */
  private void handleBlockBreakAction(BlockPositionView view) {
    Player player = view.player();
    if (player == null) {
      // PacketEvents can deliver a packet before the Bukkit player exists.
      view.release();
      return;
    }
    BlockPositionView.DigAction digType = view.digAction();
    ToolSwitchHeuristicMeta meta = metaOf(player);

    // Update breaking state ticks
    if (digType == BlockPositionView.DigAction.START_DESTROY_BLOCK) {
      meta.ticksSinceLastBreak = 0;
    } else if (digType == BlockPositionView.DigAction.STOP_DESTROY_BLOCK) {
      meta.ticksSinceLastStop = 0;
    }
    view.release();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      PacketId.Client.HELD_ITEM_SLOT_IN
    }
  )
  public void receiveHeldItemSlotChange(PacketEvent event) {
    handleHeldItemSlotChange(event.getPlayer(), event.getPacket().getIntegers().read(0));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      PacketId.Client.HELD_ITEM_SLOT_IN
    }
  )
  public void receiveHeldItemSlotChange(PacketReceiveEvent event, Player player) {
    // PacketEvents can deliver a packet before the Bukkit player exists.
    if (player == null) {
      return;
    }
    handleHeldItemSlotChange(player, new WrapperPlayClientHeldItemChange(event).getSlot());
  }

  /** Engine independent handling; the packet only carries the new hotbar slot. */
  private void handleHeldItemSlotChange(Player player, int slot) {
    User user = userOf(player);
    int currentSlot = user.meta().inventory().handSlot();
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
        flag(
          user,
          "sent suspicious slot packets while breaking blocks",
          meta.ticksSinceLastStop + " ticks since stopping"
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
