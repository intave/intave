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

package de.jpx3.intave.packet.wire.scoreboard;

import com.google.gson.JsonPrimitive;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.StringTag;
import de.jpx3.intave.packet.contract.mutable.MutableScoreboardDisplayPacket;
import de.jpx3.intave.packet.contract.mutable.MutableScoreboardObjectivePacket;
import de.jpx3.intave.packet.contract.mutable.MutableScoreboardScorePacket;
import de.jpx3.intave.packet.contract.mutable.MutableScoreboardTeamPacket;
import io.netty.buffer.ByteBuf;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.*;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.*;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;

public final class ScoreboardWirePacketTestSupport {
	private ScoreboardWirePacketTestSupport() {
	}

	public static MutableScoreboardObjectivePacket objective(int protocol, String name, int action, String title, int format) {
		return roundTrip(CLIENTBOUND, SCOREBOARD_OBJECTIVE, protocol, out -> {
			VARINT_STRING.encode(out, name);
			out.writeByte(action);
			if (action != 1) {
				component(out, protocol, title);
				if (protocol < 393) VARINT_STRING.encode(out, "hearts");
				else VARINT.encode(out, 1);
				if (protocol >= 765) numberFormat(out, format);
			}
		});
	}

	public static MutableScoreboardDisplayPacket display(int protocol, int slot, String objective) {
		return roundTrip(CLIENTBOUND, SCOREBOARD_DISPLAY_OBJECTIVE, protocol, out -> {
			if (protocol < 764) out.writeByte(slot);
			else VARINT.encode(out, slot);
			VARINT_STRING.encode(out, objective == null ? "" : objective);
		});
	}

	public static MutableScoreboardScorePacket score(int protocol, String owner, String objective, int value, String display, int format) {
		return roundTrip(CLIENTBOUND, SCOREBOARD_SCORE, protocol, out -> {
			VARINT_STRING.encode(out, owner);
			if (protocol < 765) VARINT.encode(out, 0);
			VARINT_STRING.encode(out, objective);
			VARINT.encode(out, value);
			if (protocol >= 765) {
				out.writeBoolean(display != null);
				if (display != null) component(out, protocol, display);
				numberFormat(out, format);
			}
		});
	}

	public static MutableScoreboardScorePacket reset(int protocol, String owner, String objective) {
		return roundTrip(CLIENTBOUND, protocol < 765 ? SCOREBOARD_SCORE : RESET_SCORE, protocol, out -> {
			VARINT_STRING.encode(out, owner);
			if (protocol < 765) {
				VARINT.encode(out, 1);
				VARINT_STRING.encode(out, objective == null ? "" : objective);
			} else {
				out.writeBoolean(objective != null);
				if (objective != null) VARINT_STRING.encode(out, objective);
			}
		});
	}

	public static MutableScoreboardTeamPacket team(int protocol, int action, int color, String prefix, String suffix, String... entries) {
		return roundTrip(CLIENTBOUND, SCOREBOARD_TEAM, protocol, out -> {
			VARINT_STRING.encode(out, "team");
			out.writeByte(action);
			if (action == 0 || action == 2) {
				component(out, protocol, "Team title");
				if (protocol < 393 || protocol >= 776) {
					component(out, protocol, prefix);
					component(out, protocol, suffix);
				}
				if (protocol < 776) out.writeByte(3);
				if (protocol < 770) {
					VARINT_STRING.encode(out, "hideForOtherTeams");
					if (protocol >= 107) VARINT_STRING.encode(out, "pushOwnTeam");
				} else {
					VARINT.encode(out, 2);
					VARINT.encode(out, 3);
				}
				if (protocol < 393) out.writeByte(color);
				else if (protocol < 776) VARINT.encode(out, color < 0 ? 21 : color);
				else {
					out.writeBoolean(color >= 0);
					if (color >= 0) VARINT.encode(out, color);
				}
				if (protocol >= 393 && protocol < 776) {
					component(out, protocol, prefix);
					component(out, protocol, suffix);
				}
				if (protocol >= 776) out.writeByte(3);
			}
			if (action == 0 || action == 3 || action == 4) {
				VARINT.encode(out, entries.length);
				for (String entry : entries) VARINT_STRING.encode(out, entry);
			}
		});
	}

	private static void component(ByteBuf out, int protocol, String text) {
		if (protocol < 393) VARINT_STRING.encode(out, text);
		else if (protocol < 765) VARINT_STRING.encode(out, new JsonPrimitive(text).toString());
		else TRUSTED_TAG.encode(out, new StringTag(text));
	}

	private static void numberFormat(ByteBuf out, int format) {
		out.writeBoolean(format >= 0);
		if (format < 0) return;
		VARINT.encode(out, format);
		if (format == 1) {
			CompoundTag style = new CompoundTag();
			style.putString("color", "gold");
			TRUSTED_TAG.encode(out, style);
		} else if (format == 2) {
			TRUSTED_TAG.encode(out, new StringTag("points"));
		}
	}
}
