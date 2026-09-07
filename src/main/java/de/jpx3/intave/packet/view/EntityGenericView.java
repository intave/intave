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

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Engine neutral view of an outbound packet whose payload is, for Intave's purposes, just an
 * entity id.
 * <p>
 * This is the view counterpart of {@link de.jpx3.intave.packet.reader.EntityReader}, the reader
 * every entity addressed packet reader derives from: it exposes the runtime entity id and the
 * resolution of that id to a Bukkit entity in the receiving player's world, which is all the
 * generic entity path ever reads. The one field write the family performs on top of that is the
 * block break animation's destroy stage, so it lives here too rather than forcing a second
 * near-empty family for a single setter.
 * <p>
 * Deliberately smaller than {@link EntityVelocityView} or {@link EntityMetadataView}: those
 * decode a payload, this one only needs the addressing header.
 */
public interface EntityGenericView {

  Player player();

  /** @return the runtime id of the entity the packet addresses. */
  int entityId();

  /**
   * Resolves {@link #entityId()} against the world of the player receiving the packet.
   *
   * @return the addressed entity, or null when it is not loaded or not tracked.
   */
  @Nullable Entity entity();

  /**
   * Overwrites the block break animation's destroy stage.
   * <p>
   * Stages 0 to 9 draw the crack overlay; anything outside that range tells the client to stop
   * drawing one, which is how a stale breaking update is cleared.
   */
  void setDestroyStage(int destroyStage);

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}
