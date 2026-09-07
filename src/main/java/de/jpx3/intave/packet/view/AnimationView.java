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

import de.jpx3.intave.packet.reader.AnimationReader;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

/**
 * Engine neutral view of the two outbound packets that drive bed handling: the entity animation
 * packet and the legacy use bed packet.
 * <p>
 * Mirrors {@link EntityMetadataView}: both packets are entity scoped, both consumers live in the
 * movement dispatcher and both first check whether the packet targets the user's own entity. They
 * are modelled as one family because they are two halves of the same sequence - the use bed packet
 * records where the player fell asleep, the wake up animation consumes that record - and because a
 * consumer never has to care which of the two it received beyond reading its one payload field.
 * <p>
 * Exactly the operations those two consumers perform are exposed: the entity id, the animation
 * type and the bed position. {@link #isBedUse()} says which of the two payload getters carries a
 * value; the other one is null.
 * <p>
 * The animation constants are reused from {@link AnimationReader} rather than duplicated, matching
 * {@link WindowClickView}, so a consumer keeps comparing against the very same enum whichever
 * engine delivered the packet.
 */
public interface AnimationView {

  Player player();

  /** @return the entity the animation or bed use applies to. */
  int entityId();

  /** @return true when this packet describes the given user's own entity. */
  default boolean targetEntityIdIsSameAs(User user) {
    return user.hasPlayer() && user.player().getEntityId() == entityId();
  }

  /** @return true for the use bed packet, false for the entity animation packet. */
  boolean isBedUse();

  /** @return the animation, or null when this is the use bed packet. */
  AnimationReader.Animation animation();

  /** @return the bed being entered, or null when this is the entity animation packet. */
  BlockPosition bedPosition();

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}
