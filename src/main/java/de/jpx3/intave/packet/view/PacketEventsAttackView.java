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
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.entity.Player;

/**
 * {@link AttackView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction: the combat path queries the action and the entity
 * id repeatedly, and every PacketEvents getter re-reads the packet buffer.
 */
public final class PacketEventsAttackView implements AttackView {

  private final PacketReceiveEvent event;
  private final int entityId;
  private final boolean attack;
  private final boolean secondary;

  private PacketEventsAttackView(PacketReceiveEvent event, int entityId, boolean attack, boolean secondary) {
    this.event = event;
    this.entityId = entityId;
    this.attack = attack;
    this.secondary = secondary;
  }

  /**
   * @return a view over the event, or null when the packet is not an entity interaction.
   *         PacketEvents has no separate attack packet: both {@code ATTACK} and {@code USE_ENTITY}
   *         resolve to {@code INTERACT_ENTITY}, and the attack is told apart by the action.
   */
  public static PacketEventsAttackView of(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
      return null;
    }
    WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
    WrapperPlayClientInteractEntity.InteractAction action = wrapper.getAction();
    return new PacketEventsAttackView(
      event,
      wrapper.getEntityId(),
      action == WrapperPlayClientInteractEntity.InteractAction.ATTACK,
      action == WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT
    );
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
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
  public int entityId() {
    return entityId;
  }

  @Override
  public boolean isAttackPacket() {
    return attack;
  }

  @Override
  public boolean isSecondary() {
    return secondary;
  }

  @Override
  public void release() {
    // Nothing held: the wrapper was decoded into primitives at construction.
  }
}
