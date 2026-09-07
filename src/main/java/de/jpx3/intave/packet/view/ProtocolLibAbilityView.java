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

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.packet.reader.AbilityInReader;
import de.jpx3.intave.packet.reader.AbilityOutReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * {@link AbilityView} backed by ProtocolLib. Wraps the pooled {@link AbilityInReader} or
 * {@link AbilityOutReader} so the existing reader pooling and release semantics stay exactly as
 * they were.
 * <p>
 * Three entry points, matching the shapes the ability subscriptions have:
 * <ul>
 *   <li>the two injected forms, where the subscription linker already handed the handler a pooled
 *       reader;</li>
 *   <li>the raw {@link PacketEvent} form, which resolves the direction from the packet type and
 *       acquires the pooled reader itself, as {@link ProtocolLibMovementView} does.</li>
 * </ul>
 * Exactly one of the two readers is non null; the direction decides which.
 */
public final class ProtocolLibAbilityView implements AbilityView {

  private final PacketEvent event;
  private final @Nullable AbilityInReader inReader;
  private final @Nullable AbilityOutReader outReader;

  /** Wraps an inbound reader the subscription linker already injected. */
  public ProtocolLibAbilityView(PacketEvent event, AbilityInReader reader) {
    this.event = event;
    this.inReader = reader;
    this.outReader = null;
  }

  /** Wraps an outbound reader the subscription linker already injected. */
  public ProtocolLibAbilityView(PacketEvent event, AbilityOutReader reader) {
    this.event = event;
    this.inReader = null;
    this.outReader = reader;
  }

  /**
   * Wraps a raw abilities packet event, acquiring the pooled reader for it. The caller keeps the
   * responsibility of calling {@link #release()} exactly as it did for the bare reader.
   */
  public ProtocolLibAbilityView(PacketEvent event) {
    this.event = event;
    if (event.getPacketType() == PacketType.Play.Client.ABILITIES) {
      AbilityInReader reader = PacketReaders.readerOf(event.getPacket());
      this.inReader = reader;
      this.outReader = null;
    } else {
      AbilityOutReader reader = PacketReaders.readerOf(event.getPacket());
      this.inReader = null;
      this.outReader = reader;
    }
  }

  /**
   * @return the wrapped packet event, for ability code that still needs ProtocolLib specifics -
   * notably the packet bound tick feedback, which is keyed on the ProtocolLib event.
   */
  public PacketEvent packetEvent() {
    return event;
  }

  /** @return the wrapped inbound reader, or null when this view describes an outbound packet. */
  public @Nullable AbilityInReader inReader() {
    return inReader;
  }

  /** @return the wrapped outbound reader, or null when this view describes an inbound packet. */
  public @Nullable AbilityOutReader outReader() {
    return outReader;
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public boolean inbound() {
    return inReader != null;
  }

  @Override
  public boolean requestedFlying() {
    if (inReader == null) {
      throw new IllegalStateException("requestedFlying is only carried by the inbound abilities packet");
    }
    return inReader.requestedFlying();
  }

  @Override
  public float flyingSpeed() {
    return outboundReader().flyingSpeed();
  }

  @Override
  public float walkingSpeed() {
    return outboundReader().walkingSpeed();
  }

  @Override
  public boolean flyingAllowed() {
    return outboundReader().flyingAllowed();
  }

  @Override
  public void release() {
    if (inReader != null) {
      inReader.release();
    } else if (outReader != null) {
      outReader.release();
    }
  }

  private AbilityOutReader outboundReader() {
    if (outReader == null) {
      throw new IllegalStateException("Ability speeds and the flight allowed flag are only carried by the outbound abilities packet");
    }
    return outReader;
  }
}
