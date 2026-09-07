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

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import org.bukkit.entity.Player;

/**
 * {@link RotationView} backed by ProtocolLib.
 * <p>
 * Both look packets lay their angles out the same way - yaw at float index 0, pitch at index 1 -
 * so the view reads and writes those two slots directly, exactly as the protocol scanner did
 * before this seam existed. ProtocolLib mutates the packet in place, which is why
 * {@link #release()} has nothing left to do.
 */
public final class ProtocolLibRotationView implements RotationView {

  private final PacketEvent event;
  private final StructureModifier<Float> floats;

  public ProtocolLibRotationView(PacketEvent event) {
    this.event = event;
    this.floats = event.getPacket().getFloat();
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public boolean cancelled() {
    return event.isCancelled();
  }

  @Override
  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }

  @Override
  public float yaw() {
    return floats.read(0);
  }

  @Override
  public float pitch() {
    return floats.read(1);
  }

  @Override
  public void setPitch(float pitch) {
    // writeSafely rather than write: the field is absent on packets that carry no rotation.
    floats.writeSafely(1, pitch);
  }

  @Override
  public void release() {
    // Nothing held: ProtocolLib writes straight into the packet container.
  }
}
