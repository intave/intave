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
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import org.bukkit.entity.Player;

/**
 * PacketEvents view over the 1.21.2+ {@code PLAYER_INPUT} packet, the packet that replaced
 * {@code STEER_VEHICLE} and that carries the client's key bitmask - including the sneak bit that
 * 1.21.2 moved out of {@code ENTITY_ACTION} and into
 * {@code net.minecraft.world.entity.player.Input}.
 * <p>
 * There is deliberately no shared interface and no ProtocolLib sibling. The ProtocolLib side decodes
 * the same payload through {@link de.jpx3.intave.packet.converter.InputConverter}, which hands back
 * an {@link de.jpx3.intave.share.Input}; the two engines therefore meet at the single boolean their
 * consumer branches on rather than at a view type, the same way
 * {@code MovementDispatcher#handleLegacyVehicleKeys} takes its three values directly.
 * <p>
 * <b>This class must not be loaded on a PacketEvents runtime older than the one that introduced
 * {@code WrapperPlayClientPlayerInput}.</b> It names that wrapper directly, so linking it against
 * an older jar fails. {@link #PACKET_TYPE_NAME} exists so a caller can decide whether the packet in
 * front of it is a {@code PLAYER_INPUT} <em>before</em> it touches this class: it is a compile time
 * String constant, which javac inlines into the calling class (JLS 13.1), so reading it does not
 * load this class. {@code MovementDispatcher#onInputs(PacketReceiveEvent, Player)} is that caller.
 * <p>
 * The wrapper is decoded once at construction because every PacketEvents getter re-reads the packet
 * buffer.
 */
public final class MovementPlayerInputView {

  /**
   * Name of the PacketEvents packet type this view reads.
   * <p>
   * A name and not a reference to {@code PacketType.Play.Client.PLAYER_INPUT}, for the reason
   * {@link de.jpx3.intave.module.linker.packet.pe.PacketEventsIdMapper} gives: the constant does not
   * exist on every PacketEvents release, and a hard reference to one that is missing fails with
   * {@link NoSuchFieldError} at class initialisation. Comparing
   * {@code event.getPacketType().getName()} against this answers the same question and degrades to
   * "not this packet" instead. Verified against packetevents-api 2.13.0:
   * {@code PacketType.Play.Client.PLAYER_INPUT.getName()} is exactly {@code "PLAYER_INPUT"}.
   */
  public static final String PACKET_TYPE_NAME = "PLAYER_INPUT";

  private final PacketReceiveEvent event;
  private final boolean sneaking;

  private MovementPlayerInputView(PacketReceiveEvent event, boolean sneaking) {
    this.event = event;
    this.sneaking = sneaking;
  }

  /**
   * @return a view over the event, or null when the packet is not a {@code PLAYER_INPUT}. The check
   * is repeated here even though the only caller has already made it, so that the view can never be
   * built over a {@code STEER_VEHICLE} buffer - which is a different wire format and would decode
   * into a fabricated sneak bit.
   */
  public static MovementPlayerInputView of(PacketReceiveEvent event) {
    if (event == null) {
      return null;
    }
    PacketTypeCommon type = event.getPacketType();
    if (type == null || !PACKET_TYPE_NAME.equals(type.getName())) {
      return null;
    }
    return new MovementPlayerInputView(event, new WrapperPlayClientPlayerInput(event).isShift());
  }

  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  /**
   * @return the sneak key state, read off {@code WrapperPlayClientPlayerInput#isShift()} - the
   * wrapper's own named getter for that bit, not a bit position picked out of a raw flag byte.
   * It is the same field {@link de.jpx3.intave.share.Input#sneakKey()} exposes on the ProtocolLib
   * side.
   */
  public boolean sneaking() {
    return sneaking;
  }

  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }

  /** Nothing held: the wrapper was decoded into a boolean at construction. */
  public void release() {
  }
}
