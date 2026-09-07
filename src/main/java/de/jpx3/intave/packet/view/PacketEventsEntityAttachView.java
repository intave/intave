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
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAttachEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import org.bukkit.entity.Player;

/**
 * {@link EntityAttachView} backed by PacketEvents.
 * <p>
 * Both wrappers are decoded once at construction, mirroring {@link PacketEventsAnimationView}:
 * every PacketEvents getter re-reads the packet buffer and the tracker reads each field more than
 * once. Nothing is written back, so {@link #release()} has nothing to flush.
 * <p>
 * The legacy attach packet maps field for field onto the ProtocolLib view: PacketEvents' attached
 * id is the passenger and its holding id is the vehicle, and its leash flag is the decoded form of
 * the leash integer the ProtocolLib view compares against zero.
 */
public final class PacketEventsEntityAttachView implements EntityAttachView {

  private final PacketSendEvent event;
  private final boolean mount;
  private final int vehicleId;
  private final int[] passengers;
  private final boolean leash;
  private final int passengerId;

  private PacketEventsEntityAttachView(
    PacketSendEvent event,
    boolean mount,
    int vehicleId,
    int[] passengers,
    boolean leash,
    int passengerId
  ) {
    this.event = event;
    this.mount = mount;
    this.vehicleId = vehicleId;
    this.passengers = passengers;
    this.leash = leash;
    this.passengerId = passengerId;
  }

  /** @return a view over the event, or null when the packet is neither a mount nor an attach. */
  public static PacketEventsEntityAttachView of(PacketSendEvent event) {
    PacketTypeCommon packetType = event.getPacketType();
    if (packetType == PacketType.Play.Server.SET_PASSENGERS) {
      WrapperPlayServerSetPassengers wrapper = new WrapperPlayServerSetPassengers(event);
      int[] passengers = wrapper.getPassengers();
      return new PacketEventsEntityAttachView(
        event,
        true,
        wrapper.getEntityId(),
        passengers == null ? new int[0] : passengers,
        false,
        -1
      );
    }
    if (packetType == PacketType.Play.Server.ATTACH_ENTITY) {
      WrapperPlayServerAttachEntity wrapper = new WrapperPlayServerAttachEntity(event);
      return new PacketEventsEntityAttachView(
        event,
        false,
        wrapper.getHoldingId(),
        null,
        wrapper.isLeash(),
        wrapper.getAttachedId()
      );
    }
    return null;
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public boolean isMount() {
    return mount;
  }

  @Override
  public boolean isLegacyAttach() {
    return !mount;
  }

  @Override
  public int vehicleId() {
    return vehicleId;
  }

  @Override
  public int[] passengers() {
    return passengers;
  }

  @Override
  public boolean isLeash() {
    return leash;
  }

  @Override
  public int passengerId() {
    return passengerId;
  }

  @Override
  public void release() {
    // Nothing held: the wrappers were decoded into primitives at construction and never written to.
  }
}
