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

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import de.jpx3.intave.share.Motion;
import org.bukkit.entity.Player;

/**
 * {@link EntityVelocityView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction, mirroring {@link PacketEventsAttackView}: the
 * velocity path reads the entity id and all three components repeatedly and every PacketEvents
 * getter re-reads the packet buffer. Writes are cached the same way and pushed back in
 * {@link #release()}, so an untouched packet is never re-encoded.
 * <p>
 * PacketEvents already converts the legacy fixed point encoding, so the values here are in blocks
 * per tick just like the ProtocolLib reader's.
 */
public final class PacketEventsEntityVelocityView implements EntityVelocityView {

  private final PacketSendEvent event;
  private final WrapperPlayServerEntityVelocity wrapper;
  private final int entityId;

  private double motionX;
  private double motionY;
  private double motionZ;
  private boolean dirty;

  private PacketEventsEntityVelocityView(
    PacketSendEvent event,
    WrapperPlayServerEntityVelocity wrapper,
    int entityId,
    Vector3d velocity
  ) {
    this.event = event;
    this.wrapper = wrapper;
    this.entityId = entityId;
    this.motionX = velocity.getX();
    this.motionY = velocity.getY();
    this.motionZ = velocity.getZ();
  }

  /** @return a view over the event, or null when the packet is not an entity velocity packet. */
  public static PacketEventsEntityVelocityView of(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.ENTITY_VELOCITY) {
      return null;
    }
    WrapperPlayServerEntityVelocity wrapper = new WrapperPlayServerEntityVelocity(event);
    return new PacketEventsEntityVelocityView(
      event,
      wrapper,
      wrapper.getEntityId(),
      wrapper.getVelocity()
    );
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public int entityId() {
    return entityId;
  }

  @Override
  public double motionX() {
    return motionX;
  }

  @Override
  public double motionY() {
    return motionY;
  }

  @Override
  public double motionZ() {
    return motionZ;
  }

  @Override
  public Motion motion() {
    // A fresh instance per call: consumers mutate the returned motion before writing it back.
    return new Motion(motionX, motionY, motionZ);
  }

  @Override
  public void setMotionX(double motionX) {
    this.motionX = motionX;
    this.dirty = true;
  }

  @Override
  public void setMotionZ(double motionZ) {
    this.motionZ = motionZ;
    this.dirty = true;
  }

  @Override
  public void setMotion(Motion motion) {
    this.motionX = motion.motionX();
    this.motionY = motion.motionY();
    this.motionZ = motion.motionZ();
    this.dirty = true;
  }

  @Override
  public boolean readOnly() {
    // PacketEvents hands out every send event as cancellable and writable.
    return false;
  }

  @Override
  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }

  @Override
  public void release() {
    if (!dirty) {
      return;
    }
    wrapper.setVelocity(new Vector3d(motionX, motionY, motionZ));
    event.markForReEncode(true);
    dirty = false;
  }
}
