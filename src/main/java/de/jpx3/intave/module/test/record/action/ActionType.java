package de.jpx3.intave.module.test.record.action;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public enum ActionType {
	RECEIVE_VELOCITY

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
