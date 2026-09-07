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

import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Engine neutral view of an outbound entity metadata packet.
 * <p>
 * Mirrors {@link MovementView} and {@link AttackView}: the metadata consumers (entity tracking,
 * player pose/bed tracking, entity type resolution) only ever ask for the packet's entity id and
 * for a single metadata entry by its protocol index, so those are the only operations exposed.
 * <p>
 * Metadata indices are protocol indices on both backends, so the version dependent index tables the
 * consumers already carry stay valid whichever engine delivers the packet.
 */
public interface EntityMetadataView {

  Player player();

  /** @return the entity this metadata update describes. */
  int entityId();

  /** @return true when this metadata update describes the given user's own entity. */
  default boolean targetEntityIdIsSameAs(User user) {
    return user.hasPlayer() && user.player().getEntityId() == entityId();
  }

  /**
   * @return the raw value of the metadata entry at {@code index}, or null when the packet carries
   * no entry with that index. When the packet repeats an index, the first entry wins, matching the
   * ProtocolLib reader.
   */
  Object fetchRaw(int index);

  /** @return the bed the entity is sleeping in, read from the version specific metadata index. */
  Optional<BlockPosition> bedPosition();

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}
