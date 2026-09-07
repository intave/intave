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
 * Engine neutral view of an outbound entity metadata packet, as the health filter uses it.
 * <p>
 * Separate from {@link EntityMetadataView} on purpose. That family is a read only lookup by
 * metadata index, built for the trackers; this one exists to <em>rewrite</em> one metadata entry in
 * place and hand the packet back re-encoded, and the two engines differ far more in how a metadata
 * list is written than in how it is read. Keeping the write path in its own family means the
 * tracker family stays a pure reader.
 * <p>
 * The health index this operates on is protocol index 6, the legacy health slot. The filter that
 * uses this view only runs below 1.19, matching the ProtocolLib path it was ported from.
 */
public interface EntityHealthView {

  Player player();

  /**
   * Resolves the addressed entity against the world of the player receiving the packet.
   *
   * @return the addressed entity, or null when it is not loaded or not tracked.
   */
  @Nullable Entity entity();

  /**
   * Replaces the health value the packet carries with {@code health}.
   * <p>
   * An entry reporting exactly zero health is left alone: zero is how the client is told an entity
   * died, and forging a live health value over it would desynchronise the death animation. Doing
   * nothing when the packet carries no health entry at all is equally fine.
   */
  void obscureHealth(float health);

  /**
   * Releases engine resources <em>without</em> writing anything back - the early exit taken when
   * the packet turns out not to describe an entity worth filtering.
   */
  void discard();

  /** Writes back the metadata list and releases engine resources held for this packet. */
  void release();
}
