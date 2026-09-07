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

import org.bukkit.entity.Player;

import java.util.function.IntConsumer;

/**
 * Engine neutral view of an outbound entity destroy packet.
 * <p>
 * The packet carries a list of runtime entity ids, and the encoding of that list changed three
 * times: an unsigned byte length plus fixed width ints on 1.7, a var int array up to 1.16, a single
 * var int on 1.17.0 exactly, and a var int list again from 1.17.1 on. Both backends already own
 * that version table - {@link de.jpx3.intave.packet.reader.EntityDestroyReader} on the ProtocolLib
 * side, {@code WrapperPlayServerDestroyEntities} on the PacketEvents side - so the view exposes
 * only the iteration the destroy tracker performs and nothing about the wire shape.
 * <p>
 * Deliberately an iteration rather than an array getter: the ProtocolLib backend enumerates a
 * pooled reader whose ids are read straight out of the live packet, and materialising them into an
 * array first would allocate on every destroy packet for no gain.
 */
public interface EntityDestroyView {

  Player player();

  /** Runs {@code action} once per destroyed entity id, in packet order. */
  void forEachEntityId(IntConsumer action);

  /** Releases engine resources held for this packet. */
  void release();
}
