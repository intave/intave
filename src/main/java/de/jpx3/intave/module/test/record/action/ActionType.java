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
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public enum ActionType {
	RECEIVE_VELOCITY,
	PISTON_SLIME,
	SHULKER_BOX,
	ATTACK_REDUCTION,
	ATTACK_REDUCTION_V2,
	ITEM_USE_PACKET,
	ACTIVE_ITEM_REPLACEMENT

	;
	public final static StreamCodec<ByteBuf, ByteBuf, @NotNull ActionType> STREAM_CODEC = ByteBufStreamCodecs.STRING.beforeAndAfter(
		ActionType::findByName, ActionType::name
	);

	public static ActionType findByName(String name) {
		for (ActionType type : values()) {
			if (type.name().equals(name)) {
				return type;
			}
		}
		throw new IllegalStateException("Unknown action type: " + name + ". Looks like the test you are " +
			"running has been compiled with a more recent version of Intave");
	}
}
