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

import de.jpx3.intave.packet.reader.EntityIterable;
import org.bukkit.entity.Player;

import java.util.function.IntConsumer;

/**
 * {@link EntityDestroyView} backed by ProtocolLib.
 * <p>
 * Wraps the {@link EntityIterable} the subscription linker injected rather than a raw packet event,
 * the same way {@link ProtocolLibEntityVelocityView} wraps its injected reader, so the pooled
 * {@link de.jpx3.intave.packet.reader.EntityDestroyReader} keeps its existing lifecycle and its
 * existing version handling. Iteration is delegated verbatim to
 * {@link EntityIterable#forEach(java.util.function.Consumer)}, which is exactly what the destroy
 * subscription called before this view existed.
 */
public final class ProtocolLibEntityDestroyView implements EntityDestroyView {

  private final Player player;
  private final EntityIterable iterable;

  public ProtocolLibEntityDestroyView(Player player, EntityIterable iterable) {
    this.player = player;
    this.iterable = iterable;
  }

  /** @return the wrapped iterable, for destroy code that still needs ProtocolLib specifics. */
  public EntityIterable iterable() {
    return iterable;
  }

  @Override
  public Player player() {
    return player;
  }

  @Override
  public void forEachEntityId(IntConsumer action) {
    iterable.forEach((Integer entityId) -> action.accept(entityId));
  }

  @Override
  public void release() {
    // Nothing to do: the subscription linker owns the pooled reader this iterable belongs to and
    // releases it once the subscription returns, which is what the destroy subscription relied on
    // before the view was introduced.
  }
}
