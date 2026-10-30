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

package de.jpx3.intave.share;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public enum EntityUseAction {
  ATTACK(1),
  INTERACT(0),
  INTERACT_AT(2);

  public static final StreamCodec<ByteBuf, ByteBuf, EntityUseAction> WIRE_STREAM_CODEC =
    ByteBufStreamCodecs.VARINT.beforeAndAfter(EntityUseAction::fromWireId, EntityUseAction::wireId);

  private final int wireId;

  EntityUseAction(int wireId) {
    this.wireId = wireId;
  }

  public int wireId() {
    return wireId;
  }

  private static EntityUseAction fromWireId(int id) {
    switch (id) {
      case 0: return INTERACT;
      case 1: return ATTACK;
      case 2: return INTERACT_AT;
      default: throw new IllegalArgumentException("Invalid entity-use action ID: " + id);
    }
  }
}
