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

package de.jpx3.intave.module.tracker.player;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.linker.packetperfect.PacketPerfectSubscriptionLinker;
import de.jpx3.intave.packet.ListenerStage;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.MutableScoreboardObjectivePacket;
import de.jpx3.intave.packet.event.PacketEvent;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.event.PacketSubscription;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.ClientScoreboard;
import de.jpx3.intave.user.meta.ClientScoreboard.Snapshot;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.UUID;

import static de.jpx3.intave.packet.contract.PacketType.Server.*;
import static de.jpx3.intave.packet.wire.scoreboard.ScoreboardWirePacketTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlayerScoreboardRecorderPacketTest {
  @Test
  void recordsFinalUncancelledPacketsAcrossClientAndServerVersions() throws Exception {
    Field currentVersion = MinecraftVersion.class.getDeclaredField("currentVersion");
    currentVersion.setAccessible(true);
    Object previousVersion = currentVersion.get(null);
    PacketPerfectSubscriptionLinker linker = new PacketPerfectSubscriptionLinker();
    try {
      linker.linkSubscriptionsIn(new PlayerScoreboardRecorder());
      for (MinecraftVersion server : new MinecraftVersion[]{MinecraftVersions.VER1_8_0, MinecraftVersions.VER26_3}) {
        MinecraftVersion.setCurrent(server);
        for (int protocol : new int[]{47, 107, 393, 764, 765, 766, 769, 770, 775, 776, 777}) {
          var world = FakeWorldFactory.createWorld((name, args) -> null);
          UUID id = UUID.randomUUID();
          var player = FakePlayerFactory.createPlayer((name, args) -> switch (name) {
            case "getWorld" -> world;
            case "getLocation" -> new Location(world, 0, 64, 0);
            case "getUniqueId" -> id;
            case "getName" -> "Player";
            default -> null;
          });
          verify(UserFactory.createTestUserFor(player, protocol), protocol);
        }
      }
    } finally {
      linker.disable();
      currentVersion.set(null, previousVersion);
    }
  }

  private static void verify(User user, int protocol) {
    ClientScoreboard board = user.meta().connection().clientScoreboard();
    dispatch(user, SCOREBOARD_OBJECTIVE, objective(protocol, "main", 0, "Title", -1), true);
    dispatch(user, SCOREBOARD_DISPLAY_OBJECTIVE, display(protocol, 1, "main"), false);
    assertEquals(Snapshot.empty(), board.snapshot("Player"));
    dispatch(user, SCOREBOARD_OBJECTIVE, objective(protocol, "main", 0, "Title", -1), false);
    assertEquals(text(protocol, "Title"), board.snapshot("Player").title());
    dispatch(user, SCOREBOARD_SCORE, score(protocol, "entry", "main", 4, null, -1), true);
    assertEquals(Collections.emptyList(), board.snapshot("Player").lines());
    dispatch(user, SCOREBOARD_SCORE, score(protocol, "entry", "main", 4, null, -1), false);
    assertEquals(Collections.singletonList("entry"), board.snapshot("Player").lines());

    dispatch(user, SCOREBOARD_TEAM, team(protocol, 0, 12, "[", "]", "entry", "Player"), true);
    assertEquals(Collections.singletonList("entry"), board.snapshot("Player").lines());
    dispatch(user, SCOREBOARD_TEAM, team(protocol, 0, 12, "[", "]", "entry", "Player"), false);
    assertEquals(Collections.singletonList("\u00a7c" + text(protocol, "[") + "\u00a7centry" + text(protocol, "]")), board.snapshot("Player").lines());
    dispatch(user, SCOREBOARD_TEAM, team(protocol, 2, -1, "(", ")"), false);
    assertEquals(Collections.singletonList(text(protocol, "(") + "entry" + text(protocol, ")")), board.snapshot("Player").lines());
    dispatch(user, SCOREBOARD_TEAM, team(protocol, 4, -1, "", "", "entry"), false);
    assertEquals(Collections.singletonList("entry"), board.snapshot("Player").lines());
    dispatch(user, SCOREBOARD_TEAM, team(protocol, 3, -1, "", "", "entry"), false);
    assertEquals(Collections.singletonList(text(protocol, "(") + "entry" + text(protocol, ")")), board.snapshot("Player").lines());

    dispatch(user, SCOREBOARD_OBJECTIVE, objective(protocol, "colored", 0, "Red", -1), false);
    dispatch(user, SCOREBOARD_DISPLAY_OBJECTIVE, display(protocol, 15, "colored"), false);
    dispatch(user, SCOREBOARD_SCORE, score(protocol, "entry", "colored", 1, null, -1), false);
    dispatch(user, SCOREBOARD_TEAM, team(protocol, 2, 12, "", ""), false);
    assertEquals(text(protocol, "Red"), board.snapshot("Player").title());
    dispatch(user, SCOREBOARD_TEAM, team(protocol, 1, 12, "", ""), false);
    assertEquals(text(protocol, "Title"), board.snapshot("Player").title());

    PacketType.Server resetType = protocol < 765 ? SCOREBOARD_SCORE : RESET_SCORE;
    dispatch(user, resetType, reset(protocol, "entry", "main"), true);
    assertEquals(Collections.singletonList("entry"), board.snapshot("Player").lines());
    dispatch(user, resetType, reset(protocol, "entry", "main"), false);
    assertEquals(Collections.emptyList(), board.snapshot("Player").lines());
    dispatch(user, SCOREBOARD_DISPLAY_OBJECTIVE, display(protocol, 1, "colored"), false);
    assertEquals(Collections.singletonList("entry"), board.snapshot("Player").lines());
    dispatch(user, resetType, reset(protocol, "entry", null), false);
    assertEquals(Collections.emptyList(), board.snapshot("Player").lines());

    if (protocol >= 765) {
      dispatch(user, SCOREBOARD_SCORE, score(protocol, "entry", "colored", 3, "Custom", 1), false);
      assertEquals(Collections.singletonList("\u00a7fCustom"), board.snapshot("Player").lines());
    }
    Snapshot shown = board.snapshot("Player");
    dispatch(user, SCOREBOARD_DISPLAY_OBJECTIVE, display(protocol, 1, null), true);
    assertEquals(shown, board.snapshot("Player"));
    dispatch(user, SCOREBOARD_DISPLAY_OBJECTIVE, display(protocol, 1, null), false);
    assertEquals(Snapshot.empty(), board.snapshot("Player"));
    dispatch(user, SCOREBOARD_DISPLAY_OBJECTIVE, display(protocol, 1, "colored"), false);
    dispatch(user, SCOREBOARD_OBJECTIVE, objective(protocol, "colored", 1, "", -1), true);
    assertEquals(shown, board.snapshot("Player"));
    dispatch(user, SCOREBOARD_OBJECTIVE, objective(protocol, "colored", 1, "", -1), false);
    assertEquals(Snapshot.empty(), board.snapshot("Player"));

    PacketSubscription editor = new PacketSubscription() {
      @Override public ListenerStage stage() { return ListenerStage.MUTABLE; }
      @Override public void onPacket(PacketEvent event) {
        ((MutableScoreboardObjectivePacket) event.packet()).setDisplayNameJson("{\"text\":\"Edited\",\"color\":\"gold\"}");
      }
    };
    PacketEventBus.addListener(editor, SCOREBOARD_OBJECTIVE);
    try {
      dispatch(user, SCOREBOARD_DISPLAY_OBJECTIVE, display(protocol, 1, "main"), false);
      dispatch(user, SCOREBOARD_OBJECTIVE, objective(protocol, "main", 2, "Before", -1), false);
      assertEquals("\u00a76Edited", board.snapshot("Player").title());
    } finally {
      PacketEventBus.removeListener(editor);
    }
  }

  private static String text(int protocol, String value) {
    return protocol < 393 ? value : "\u00a7f" + value;
  }

  private static void dispatch(User user, PacketType.Server type, PacketContract packet, boolean cancelled) {
    PacketSendEvent event = new PacketSendEvent(user, type, packet);
    event.setCancelled(cancelled);
    assertEquals(cancelled ? 0 : 1, PacketEventBus.dispatch(event).size());
  }
}
