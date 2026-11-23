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

import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.mutable.MutableScoreboardScorePacket;
import de.jpx3.intave.packet.contract.mutable.MutableScoreboardTeamPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.SCOREBOARD_SCORE;
import static de.jpx3.intave.packet.contract.PacketType.Server.SCOREBOARD_TEAM;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.*;
import static de.jpx3.intave.packet.wire.scoreboard.ScoreboardWirePacketTestSupport.*;
import static de.jpx3.intave.share.ScoreboardTeamAction.REMOVE;
import static org.junit.jupiter.api.Assertions.*;

final class ScoreboardWirePacketCodecsTest {
	@Test
	void objectivesDisplaysAndScoresRoundTripInEveryRegisteredProtocol() {
		for (int protocol : registry(CLIENTBOUND).supportedProtocolVersions()) {
			for (int action = 0; action <= 2; action++) {
				for (int format = -1; format <= (protocol >= 765 ? 2 : -1); format++) {
					var packet = objective(protocol, "objective", action, "\u00a7aTitle", format);
					assertEquals(action, packet.action().id());
					assertEquals("objective", packet.objectiveName());
					if (action == 1) assertNull(packet.displayNameJson());
					else assertTrue(packet.displayNameText().endsWith("\u00a7aTitle"));
					assertFalse(packet.couldHaveBeenMutated());
					packet.setObjectiveName("renamed");
					assertSamePayload(objective(protocol, "renamed", action, "\u00a7aTitle", format), packet);
				}
			}
			assertEquals("sidebar", display(protocol, 1, "objective").displaySlot());
			assertEquals("team_red", display(protocol, 15, "objective").displaySlot());
			assertTrue(display(protocol, 1, null).clearsDisplaySlot());
			for (int format = -1; format <= (protocol >= 765 ? 2 : -1); format++) {
				for (String text : Arrays.asList(null, "Custom")) {
					var score = score(protocol, "owner", "objective", -1234, text, format);
					assertEquals(-1234, score.value());
					assertFalse(score.removed());
					assertEquals(protocol >= 765 && text != null, score.hasDisplayNameJson());
					score.setValue(3456);
					assertSamePayload(score(protocol, "owner", "objective", 3456, text, format), score);
				}
			}
			for (String objective : Arrays.asList(null, "objective")) {
				var reset = reset(protocol, "owner", objective);
				assertTrue(reset.removed());
				assertNull(reset.value());
				assertNull(reset.displayNameJson());
				assertEquals(objective, reset.objectiveName());
				assertEquals("owner", reset.owner());
				assertFalse(reset.couldHaveBeenMutated());
			}
		}
	}

	@Test
	void teamActionsPreserveParametersAndMembershipAcrossEveryLayout() {
		for (int protocol : registry(CLIENTBOUND).supportedProtocolVersions()) {
			for (int action = 0; action <= 4; action++) {
				for (int color : new int[]{-1, 0, 12, 15}) {
					var team = team(protocol, action, color, "[", "]", "Player", "entry");
					assertEquals(action, team.action().id());
					assertFalse(team.couldHaveBeenMutated());
					if (action == 0 || action == 2) {
						assertEquals(color == -1 ? "reset" : color == 0 ? "black" : color == 12 ? "red" : "white", team.color());
						assertEquals(protocol < 393 ? "[" : "\u00a7f[", team.prefixText());
						assertEquals(protocol < 393 ? "]" : "\u00a7f]", team.suffixText());
						team.setColor("blue");
						assertSamePayload(team(protocol, action, 9, "[", "]", "Player", "entry"), team);
					} else {
						assertNull(team.prefixJson());
						assertNull(team.suffixJson());
						assertNull(team.color());
					}
					assertEquals(action == 0 || action >= 3 ? Arrays.asList("Player", "entry") : Collections.emptyList(), team.entries());
				}
			}
		}
	}

	@Test
	void mutableTeamsAndModernDisplayNamesCanBeReencoded() {
		for (int protocol : new int[]{47, 107, 393, 765, 770, 776, 777}) {
			var team = team(protocol, 0, 12, "[", "]", "Player");
			team.setPrefixJson("{\"text\":\"VIP\",\"color\":\"gold\",\"bold\":true}");
			team.setSuffixJson("\"!\"");
			team.setEntries(Arrays.asList("new", "entry"));
			MutableScoreboardTeamPacket edited = roundTrip(CLIENTBOUND, SCOREBOARD_TEAM, protocol, out -> encodePayload(team, out));
			assertEquals("\u00a76\u00a7lVIP", edited.prefixText());
			assertEquals(Arrays.asList("new", "entry"), edited.entries());
			assertThrows(UnsupportedOperationException.class, () -> edited.entries().add("oops"));
			team.setAction(REMOVE);
			assertNull(team.prefixJson());
			assertSamePayload(team(protocol, 1, 12, "", ""), team);
		}
		var score = score(777, "owner", "objective", 1, null, 2);
		score.setDisplayNameJson("{\"text\":\"Named\",\"italic\":true}");
		MutableScoreboardScorePacket edited = roundTrip(CLIENTBOUND, SCOREBOARD_SCORE, 777, out -> encodePayload(score, out));
		assertEquals("\u00a7f\u00a7oNamed", edited.displayNameText());
	}

	private static void assertSamePayload(PacketContract expected, PacketContract actual) {
		ByteBuf first = Unpooled.buffer(), second = Unpooled.buffer();
		try {
			encodePayload(expected, first);
			encodePayload(actual, second);
			assertEquals(first, second);
		} finally {
			first.release();
			second.release();
		}
	}
}
