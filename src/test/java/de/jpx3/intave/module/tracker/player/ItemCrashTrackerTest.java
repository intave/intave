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
import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.module.linker.packetperfect.PacketPerfectSubscriptionLinker;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.share.MinecraftKey;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.InventoryMetadata;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT_STRING;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Client.UPDATE_SIGN;
import static de.jpx3.intave.packet.contract.PacketType.Client.WINDOW_CLICK;
import static de.jpx3.intave.packet.contract.PacketType.Server.SET_SLOT;
import static de.jpx3.intave.packet.contract.PacketType.Server.WINDOW_ITEMS;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class ItemCrashTrackerTest {
	private final PacketPerfectSubscriptionLinker linker = new PacketPerfectSubscriptionLinker();
	private final List<String> kicks = new ArrayList<>();
	private User user;
	private boolean inventoryFaults;

	@BeforeEach
	void linkTracker() {
		inventoryFaults = FaultKicks.INVENTORY_FAULTS;
		FaultKicks.INVENTORY_FAULTS = true;
		MinecraftVersion.setCurrent(new MinecraftVersion("26.3"));
		var world = FakeWorldFactory.createWorld((name, args) -> null);
		UUID id = UUID.randomUUID();
		var player = FakePlayerFactory.createPlayer((name, args) -> switch (name) {
			case "getWorld" -> world;
			case "getLocation" -> new Location(world, 0, 64, 0);
			case "getUniqueId" -> id;
			default -> null;
		});
		User delegate = UserFactory.createTestUserFor(player, 777);
		user = (User) Proxy.newProxyInstance(User.class.getClassLoader(), new Class<?>[]{User.class}, (proxy, method, args) -> {
			if (method.getName().equals("kick")) {
				kicks.add((String) args[0]);
				return null;
			}
			return method.invoke(delegate, args);
		});
		linker.linkSubscriptionsIn(new ItemCrashTracker());
	}

	@AfterEach
	void unlinkTracker() {
		linker.disable();
		FaultKicks.INVENTORY_FAULTS = inventoryFaults;
	}

	@Test
	void whitelistsOnlySkullOwnersFromDeliveredSlotsContentsAndCursor() {
		for (int protocol : new int[]{47, 393, 404, 756, 766, 768, 777}) {
			ItemStack head = stack(protocol, protocol < 393 ? "skull" : "player_head", "slot" + protocol);
			ItemStack ordinary = stack(protocol, "stone", "ignored" + protocol);
			ItemStack unnamed = stack(protocol, protocol < 393 ? "skull" : "player_head", null);
			PacketContract single = singleSlot(protocol, head);
			send(SET_SLOT, single, true);
			assertFalse(inventory().skullWhitelisted("slot" + protocol));
			send(SET_SLOT, single, false);
			assertTrue(inventory().skullWhitelisted("slot" + protocol));
			send(SET_SLOT, singleSlot(protocol, null), false);
			send(SET_SLOT, singleSlot(protocol, ordinary), false);
			assertFalse(inventory().skullWhitelisted("ignored" + protocol));

			head.setSkullOwnerName("bulk" + protocol);
			ItemStack cursor = stack(protocol, protocol < 393 ? "skull" : "player_head", "cursor" + protocol);
			PacketContract bulk = roundTrip(CLIENTBOUND, WINDOW_ITEMS, protocol, out -> {
				if (protocol < 768) out.writeByte(0);
				else VARINT.encode(out, 0);
				if (protocol < 756) out.writeShort(4);
				else {
					VARINT.encode(out, 7);
					VARINT.encode(out, 4);
				}
				for (ItemStack item : new ItemStack[]{null, head, ordinary, unnamed}) {
					ItemStack.codecFor(protocol).encode(out, item);
				}
				if (protocol >= 756) ItemStack.codecFor(protocol).encode(out, cursor);
			});
			send(WINDOW_ITEMS, bulk, true);
			assertFalse(inventory().skullWhitelisted("bulk" + protocol));
			assertFalse(inventory().skullWhitelisted("cursor" + protocol));
			send(WINDOW_ITEMS, bulk, false);
			assertTrue(inventory().skullWhitelisted("bulk" + protocol));
			assertEquals(protocol >= 756, inventory().skullWhitelisted("cursor" + protocol));
			assertFalse(inventory().skullWhitelisted("ignored" + protocol));
		}
		assertTrue(kicks.isEmpty());
	}

	@Test
	void rejectsOverlongSignLinesAndIgnoresCancelledUpdates() {
		for (int protocol : new int[]{47, 107, 477, 762, 763, 776, 777}) {
			for (int line = 0; line < 4; line++) {
				PacketContract allowed = sign(protocol, line, 500);
				assertFalse(receive(UPDATE_SIGN, allowed, false).isCancelled());
				int previousKicks = kicks.size();
				PacketContract oversized = sign(protocol, line, 501);
				receive(UPDATE_SIGN, oversized, true);
				assertEquals(previousKicks, kicks.size());
				assertTrue(receive(UPDATE_SIGN, oversized, false).isCancelled());
				assertEquals(previousKicks + 1, kicks.size());
				assertEquals("Too many characters in sign update packet", kicks.get(previousKicks));
			}
		}
	}

	@Test
	void preservesClickThresholdResetAndFaultToggleAcrossClickContracts() {
		for (int protocol : new int[]{47, 107, 755, 756, 768, 770, 777}) {
			PacketContract packet = WirePackets.createPlayPacket(WINDOW_CLICK, protocol);
			inventory().lastWCCReset = System.currentTimeMillis();
			inventory().windowClickCounter = 500;
			int previousKicks = kicks.size();
			receive(WINDOW_CLICK, packet, true);
			assertEquals(500, inventory().windowClickCounter);
			assertFalse(receive(WINDOW_CLICK, packet, false).isCancelled());
			assertEquals(501, inventory().windowClickCounter);
			assertTrue(receive(WINDOW_CLICK, packet, false).isCancelled());
			assertEquals(502, inventory().windowClickCounter);
			assertEquals(previousKicks + 1, kicks.size());
			assertEquals("Too many inventory interactions", kicks.get(previousKicks));

			FaultKicks.INVENTORY_FAULTS = false;
			assertFalse(receive(WINDOW_CLICK, packet, false).isCancelled());
			assertEquals(previousKicks + 1, kicks.size());
			FaultKicks.INVENTORY_FAULTS = true;
			inventory().lastWCCReset = System.currentTimeMillis() - 10001;
			assertFalse(receive(WINDOW_CLICK, packet, false).isCancelled());
			assertEquals(1, inventory().windowClickCounter);
		}
	}

	private InventoryMetadata inventory() {
		return user.meta().inventory();
	}

	private void send(PacketType.Server type, PacketContract packet, boolean cancelled) {
		PacketSendEvent event = new PacketSendEvent(user, type, packet);
		event.setCancelled(cancelled);
		PacketEventBus.dispatch(event);
	}

	private PacketReceiveEvent receive(PacketType.Client type, PacketContract packet, boolean cancelled) {
		PacketReceiveEvent event = new PacketReceiveEvent(user, type, packet);
		event.setCancelled(cancelled);
		PacketEventBus.dispatch(event);
		return event;
	}

	private static ItemStack stack(int protocol, String type, String owner) {
		ItemStack stack = new ItemStack(MinecraftKey.withDefaultNamespace(type), 1, protocol);
		if (protocol < 393) stack.setLegacyData((short) (type.equals("skull") ? 3 : 0));
		stack.setSkullOwnerName(owner);
		return stack;
	}

	private static PacketContract singleSlot(int protocol, ItemStack stack) {
		return roundTrip(CLIENTBOUND, SET_SLOT, protocol, out -> {
			if (protocol < 768) out.writeByte(0);
			else VARINT.encode(out, 0);
			if (protocol >= 756) VARINT.encode(out, 7);
			out.writeShort(4);
			ItemStack.codecFor(protocol).encode(out, stack);
		});
	}

	private static PacketContract sign(int protocol, int longLine, int length) {
		return roundTrip(SERVERBOUND, UPDATE_SIGN, protocol, out -> {
			out.writeLong(0);
			if (protocol >= 763 && protocol < 777) out.writeBoolean(false);
			for (int i = 0; i < 4; i++) {
				String line = i == longLine ? "x".repeat(length - (protocol < 107 ? 11 : 0)) : "";
				VARINT_STRING.encode(out, protocol < 107 ? "{\"text\":\"" + line + "\"}" : line);
			}
			if (protocol >= 777) VARINT.encode(out, 0);
		});
	}
}
