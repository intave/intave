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

package de.jpx3.intave.packet.view;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import de.jpx3.intave.packet.converter.PlayerAction;
import org.bukkit.entity.Player;

/**
 * {@link PlayerActionView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction because every PacketEvents getter re-reads the
 * packet buffer. PacketEvents already normalises the per-version action ids, so the legacy 1.8
 * spellings ({@link PlayerAction#PRESS_SHIFT_KEY} / {@link PlayerAction#RELEASE_SHIFT_KEY}) never
 * appear here - they arrive as {@link PlayerAction#START_SNEAKING} / {@link PlayerAction#STOP_SNEAKING}
 * instead. Every consumer branches through {@code isStartSneak()} / {@code isStopSneak()} or lists
 * both spellings in its switch, so the observable behaviour is unchanged.
 */
public final class PacketEventsPlayerActionView implements PlayerActionView {

  private final PacketReceiveEvent event;
  private final PlayerAction action;

  private PacketEventsPlayerActionView(PacketReceiveEvent event, PlayerAction action) {
    this.event = event;
    this.action = action;
  }

  /**
   * @return a view over the event, or null when the packet is not an entity action packet or
   * carries an action Intave has no equivalent for.
   */
  public static PacketEventsPlayerActionView of(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.ENTITY_ACTION) {
      return null;
    }
    PlayerAction action = convert(new WrapperPlayClientEntityAction(event).getAction());
    return action == null ? null : new PacketEventsPlayerActionView(event, action);
  }

  private static PlayerAction convert(WrapperPlayClientEntityAction.Action action) {
    if (action == null) {
      return null;
    }
    switch (action) {
      case START_SNEAKING:
        return PlayerAction.START_SNEAKING;
      case STOP_SNEAKING:
        return PlayerAction.STOP_SNEAKING;
      case LEAVE_BED:
        return PlayerAction.STOP_SLEEPING;
      case START_SPRINTING:
        return PlayerAction.START_SPRINTING;
      case STOP_SPRINTING:
        return PlayerAction.STOP_SPRINTING;
      case START_JUMPING_WITH_HORSE:
        return PlayerAction.START_RIDING_JUMP;
      case STOP_JUMPING_WITH_HORSE:
        return PlayerAction.STOP_RIDING_JUMP;
      case OPEN_HORSE_INVENTORY:
        return PlayerAction.OPEN_INVENTORY;
      case START_FLYING_WITH_ELYTRA:
        return PlayerAction.START_FALL_FLYING;
      default:
        return null;
    }
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public PlayerAction playerAction() {
    return action;
  }

  @Override
  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }

  @Override
  public void release() {
    // Nothing held: the wrapper was decoded into an enum constant at construction.
  }
}
