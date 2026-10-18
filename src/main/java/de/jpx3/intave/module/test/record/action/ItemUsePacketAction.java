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

package de.jpx3.intave.module.test.record.action;


import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.module.test.record.TickRange;
import de.jpx3.intave.share.PlayerHand;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** A client item-use packet received between two recorded movement frames. */
public final class ItemUsePacketAction extends Action {
	public enum Packet {
		USE_MAIN_HAND,
		USE_OFF_HAND,
		RELEASE_USE_ITEM,
		HELD_ITEM_SLOT
	}

	private static final StreamCodec<ByteBuf, ByteBuf, Packet> PACKET_CODEC =
		ByteBufStreamCodecs.STRING.beforeAndAfter(Packet::valueOf, Packet::name);

	public static final StreamCodec<ByteBuf, ByteBuf, ItemUsePacketAction> STREAM_CODEC =
		StreamCodec.compound(
			PACKET_CODEC, ItemUsePacketAction::packet,
			ByteBufStreamCodecs.INTEGER, ItemUsePacketAction::slot,
			TickRange.STREAM_CODEC, ItemUsePacketAction::tickRange,
			ItemUsePacketAction::new
		);

	private final Packet packet;
	private final int slot;
	private final TickRange tickRange;

	public ItemUsePacketAction(Packet packet, int slot, TickRange tickRange) {
		this.packet = Objects.requireNonNull(packet, "packet");
		this.tickRange = Objects.requireNonNull(tickRange, "tickRange");
		if (packet == Packet.HELD_ITEM_SLOT && (slot < 0 || slot > 8)) {
			throw new IllegalArgumentException("Invalid held item slot: " + slot);
		}
		this.slot = slot;
	}

	public static ItemUsePacketAction useItem(PlayerHand hand, long tick) {
		Packet packet = hand == PlayerHand.OFF_HAND
			? Packet.USE_OFF_HAND : Packet.USE_MAIN_HAND;
		return new ItemUsePacketAction(packet, -1, oneTick(tick));
	}

	public static ItemUsePacketAction releaseUseItem(long tick) {
		return new ItemUsePacketAction(Packet.RELEASE_USE_ITEM, -1, oneTick(tick));
	}

	public static ItemUsePacketAction heldItemSlot(int slot, long tick) {
		return new ItemUsePacketAction(Packet.HELD_ITEM_SLOT, slot, oneTick(tick));
	}

	private static TickRange oneTick(long tick) {
		return TickRange.betweenExclusive(tick, tick + 1);
	}

	public Packet packet() {
		return packet;
	}

	public int slot() {
		return slot;
	}

	public TickRange tickRange() {
		return tickRange;
	}

	@Override
	public @NotNull ActionType type() {
		return ActionType.ITEM_USE_PACKET;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		return object instanceof ItemUsePacketAction
			&& packet == ((ItemUsePacketAction) object).packet
			&& slot == ((ItemUsePacketAction) object).slot
			&& tickRange.equals(((ItemUsePacketAction) object).tickRange);
	}

	@Override
	public int hashCode() {
		return Objects.hash(packet, slot, tickRange);
	}

	@Override
	public String toString() {
		return "ItemUsePacketAction{packet=" + packet + ", slot=" + slot
			+ ", tickRange=" + tickRange + '}';
	}
}
