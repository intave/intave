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

package de.jpx3.intave.packet.wire.block;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.immutable.BlockBreakAckPacket;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BlockUpdate;
import de.jpx3.intave.share.PlayerDigAction;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Objects;

public final class BlockBreakAckWirePacket implements BlockBreakAckPacket {
  public static final StreamCodec<ByteBuf, ByteBuf, BlockBreakAckWirePacket> STREAM_CODEC = StreamCodec.compound(
    BlockUpdate.STREAM_CODEC, BlockBreakAckWirePacket::blockUpdate,
    ByteBufStreamCodecs.enumCodec(Arrays.copyOf(PlayerDigAction.values(), 7)), BlockBreakAckWirePacket::action,
    ByteBufStreamCodecs.BOOLEAN, BlockBreakAckWirePacket::successful,
    BlockBreakAckWirePacket::new
  );

  private final BlockUpdate update;
  private final PlayerDigAction action;
  private final boolean successful;

  public BlockBreakAckWirePacket() {
    this(new BlockUpdate(BlockPosition.ORIGIN, 0), PlayerDigAction.START_DESTROY_BLOCK, false);
  }

  public BlockBreakAckWirePacket(BlockUpdate update, PlayerDigAction action, boolean successful) {
    this.update = Objects.requireNonNull(update, "update");
    this.action = Objects.requireNonNull(action, "action");
    if (action.ordinal() > PlayerDigAction.SWAP_HELD_ITEMS.ordinal()) {
      throw new IllegalArgumentException("Unsupported digging acknowledgement action: " + action);
    }
    this.successful = successful;
  }

  @Override
  public boolean appliesTo(int protocolVersion) {
    return protocolVersion >= 498 && protocolVersion < 759;
  }

  @Override
  public BlockUpdate blockUpdate() {
    return update;
  }

  @Override
  public PlayerDigAction action() {
    return action;
  }

  @Override
  public boolean successful() {
    return successful;
  }
}
