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

import ac.intave.samples.event.InventoryActionEvent;
import org.bukkit.entity.Player;

/**
 * Engine neutral view of the thin inventory packets that carry a single identifying field.
 * <p>
 * Two packet shapes share this family because their consumers in the sample dispatcher read exactly
 * one field each and nothing else:
 * <ul>
 *   <li>the close window packets - both the client's and the server's - of which only the container
 *   id is ever read ({@code WindowIdReader} on the ProtocolLib side);</li>
 *   <li>the block dig packet, of which only the drop / swap held item action is ever read
 *   ({@code BlockDigReader} on the ProtocolLib side).</li>
 * </ul>
 * A view instance always wraps one of the two shapes, and the accessor belonging to the other shape
 * throws: consumers subscribe per packet, so they already know which field their packet carries.
 * Mirrors {@link MovementView} and {@link WindowClickView} in exposing only the operations the
 * consumers actually perform, so both backends satisfy it without leaking engine specific types.
 */
public interface WindowIdView {

  Player player();

  /**
   * @return the container id carried by a close window packet.
   * @throws IllegalStateException when this view wraps a block dig packet.
   */
  int containerId();

  /**
   * The drop / swap action a block dig packet describes, already normalised to the sample action
   * the inventory samples expose.
   *
   * @return one of {@code DROP_HELD_ONE}, {@code DROP_HELD_STACK} or {@code SWAP_HANDS}; never null
   * on a view handed out by the backends, which decline to build one for any other dig action.
   * @throws IllegalStateException when this view wraps a close window packet.
   */
  InventoryActionEvent.Action heldItemAction();

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}
