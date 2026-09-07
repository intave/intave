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
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities;
import org.bukkit.entity.Player;

/**
 * {@link AbilityView} backed by PacketEvents.
 * <p>
 * Unlike the ProtocolLib view there is no reader pool here: the wrapper is decoded once into
 * primitives at construction, so {@link #release()} has nothing to do.
 * <p>
 * Field mapping against the ProtocolLib readers this replaces:
 * <ul>
 *   <li>inbound, {@code AbilityInReader.requestedFlying()} - the reader reads boolean 0 on 1.16 and
 *       above (where the packet is a single bit field) and boolean 1 below that (where it is the
 *       second of four flags). Both are the flying flag, which PacketEvents decodes for every
 *       version as {@code isFlying()}, so the version switch disappears here;</li>
 *   <li>outbound, {@code AbilityOutReader.flyingSpeed()} - float 0, PacketEvents'
 *       {@code getFlySpeed()};</li>
 *   <li>outbound, {@code AbilityOutReader.walkingSpeed()} - float 1. PacketEvents names that field
 *       after what the client does with it, {@code getFOVModifier()}; it is the same wire field the
 *       reader calls the walking speed;</li>
 *   <li>outbound, {@code AbilityOutReader.flyingAllowed()} - boolean 2, PacketEvents'
 *       {@code isFlightAllowed()}.</li>
 * </ul>
 */
public final class PacketEventsAbilityView implements AbilityView {

  private final ProtocolPacketEvent event;
  private final boolean inbound;
  private final boolean requestedFlying;
  private final float flyingSpeed;
  private final float walkingSpeed;
  private final boolean flyingAllowed;

  private PacketEventsAbilityView(PacketReceiveEvent event, WrapperPlayClientPlayerAbilities wrapper) {
    this.event = event;
    this.inbound = true;
    this.requestedFlying = wrapper.isFlying();
    this.flyingSpeed = 0F;
    this.walkingSpeed = 0F;
    this.flyingAllowed = false;
  }

  private PacketEventsAbilityView(PacketSendEvent event, WrapperPlayServerPlayerAbilities wrapper) {
    this.event = event;
    this.inbound = false;
    this.requestedFlying = false;
    this.flyingSpeed = wrapper.getFlySpeed();
    this.walkingSpeed = wrapper.getFOVModifier();
    this.flyingAllowed = wrapper.isFlightAllowed();
  }

  /** @return a view over the event, or null when the packet is not the inbound abilities packet. */
  public static PacketEventsAbilityView of(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.PLAYER_ABILITIES) {
      return null;
    }
    return new PacketEventsAbilityView(event, new WrapperPlayClientPlayerAbilities(event));
  }

  /** @return a view over the event, or null when the packet is not the outbound abilities packet. */
  public static PacketEventsAbilityView of(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.PLAYER_ABILITIES) {
      return null;
    }
    return new PacketEventsAbilityView(event, new WrapperPlayServerPlayerAbilities(event));
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public boolean inbound() {
    return inbound;
  }

  @Override
  public boolean requestedFlying() {
    if (!inbound) {
      throw new IllegalStateException("requestedFlying is only carried by the inbound abilities packet");
    }
    return requestedFlying;
  }

  @Override
  public float flyingSpeed() {
    requireOutbound();
    return flyingSpeed;
  }

  @Override
  public float walkingSpeed() {
    requireOutbound();
    return walkingSpeed;
  }

  @Override
  public boolean flyingAllowed() {
    requireOutbound();
    return flyingAllowed;
  }

  @Override
  public void release() {
    // Nothing held: the wrapper was decoded into primitives at construction.
  }

  private void requireOutbound() {
    if (inbound) {
      throw new IllegalStateException("Ability speeds and the flight allowed flag are only carried by the outbound abilities packet");
    }
  }
}
