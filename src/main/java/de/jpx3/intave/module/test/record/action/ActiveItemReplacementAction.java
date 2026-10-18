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
import io.netty.buffer.ByteBuf;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** A clientbound replacement of the stack whose object is currently being used. */
public final class ActiveItemReplacementAction extends Action {
	public static final StreamCodec<ByteBuf, ByteBuf, ActiveItemReplacementAction> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.MATERIAL, ActiveItemReplacementAction::replacementType,
			TickRange.STREAM_CODEC, ActiveItemReplacementAction::tickRange,
			ActiveItemReplacementAction::new
		);

	private final Material replacementType;
	private final TickRange tickRange;

	public ActiveItemReplacementAction(Material replacementType, TickRange tickRange) {
		this.replacementType = Objects.requireNonNull(replacementType, "replacementType");
		this.tickRange = Objects.requireNonNull(tickRange, "tickRange");
	}

	public static ActiveItemReplacementAction at(Material replacementType, long tick) {
		return new ActiveItemReplacementAction(
			replacementType, TickRange.betweenExclusive(tick, tick + 1)
		);
	}

	public Material replacementType() {
		return replacementType;
	}

	public TickRange tickRange() {
		return tickRange;
	}

	@Override
	public @NotNull ActionType type() {
		return ActionType.ACTIVE_ITEM_REPLACEMENT;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		return object instanceof ActiveItemReplacementAction
			&& replacementType == ((ActiveItemReplacementAction) object).replacementType
			&& tickRange.equals(((ActiveItemReplacementAction) object).tickRange);
	}

	@Override
	public int hashCode() {
		return Objects.hash(replacementType, tickRange);
	}

	@Override
	public String toString() {
		return "ActiveItemReplacementAction{replacementType=" + replacementType
			+ ", tickRange=" + tickRange + '}';
	}
}
