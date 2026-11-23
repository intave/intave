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

import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.module.feedback.NativeFeedbackTestSupport;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.mutable.MutableBlockDigPacket;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.packet.wire.item.CarriedItemSlotWirePacket;
import de.jpx3.intave.packet.wire.item.HeldItemSlotWirePacket;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.share.*;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Client.*;
import static de.jpx3.intave.packet.contract.PacketType.Server.*;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class PlayerHandTrackerPacketPerfectTest {
	private NativeFeedbackTestSupport feedback;
	private final List<Player> players = new ArrayList<>();

	@BeforeEach
	void linkTracker() throws Exception {
		feedback = new NativeFeedbackTestSupport();
		ItemProperties.setup();
		feedback.linker.linkSubscriptionsIn(new PlayerHandTracker());
	}

	@AfterEach
	void unlinkTracker() throws Exception {
		players.forEach(UserRepository::unregisterUser);
		feedback.close();
	}

	@Test
	void nativeUsePacketsRespectLegacyAirUseModernHandsAndCancellation() {
		for (int protocol : new int[]{47, 107, 477, 759, 767, 777}) {
			User user = user(protocol);
			held(user, Material.GOLDEN_APPLE);
			PacketType.Client type = protocol < 107 ? USE_ITEM_ON : USE_ITEM;
			PacketContract use = use(protocol, PlayerHand.MAIN_HAND);
			receive(user, type, use, true);
			assertFalse(user.meta().inventory().handActive());
			if (protocol == 47) {
				PacketContract placement = roundTrip(SERVERBOUND, USE_ITEM_ON, 47,
					out -> out.writeLong(0).writeByte(1).writeShort(-1).writeByte(0).writeByte(0).writeByte(0));
				receive(user, USE_ITEM_ON, placement, false);
				assertFalse(user.meta().inventory().handActive());
			}
			receive(user, type, use, false);
			assertTrue(user.meta().inventory().handActive(), "protocol " + protocol);
			assertEquals(PlayerHand.MAIN_HAND, user.meta().inventory().activeHand());
			if (protocol >= 107) {
				user.meta().inventory().deactivateHand();
				user.player().getInventory().setItemInOffHand(new org.bukkit.inventory.ItemStack(Material.SHIELD));
				receive(user, USE_ITEM, use(protocol, PlayerHand.OFF_HAND), false);
				assertEquals(PlayerHand.OFF_HAND, user.meta().inventory().activeHand());
			}
		}
	}

	@Test
	void useOnBlockOnlyStartsBrushAndRecentSwordPunishmentCancelsUse() {
		User modern = user(777);
		held(modern, Material.BOW);
		modern.player().getInventory().setItem(1, new org.bukkit.inventory.ItemStack(Material.ARROW));
		PacketContract onBlock = WirePackets.createPlayPacket(USE_ITEM_ON, 777);
		receive(modern, USE_ITEM_ON, onBlock, false);
		assertFalse(modern.meta().inventory().handActive());
		held(modern, Material.BRUSH);
		receive(modern, USE_ITEM_ON, onBlock, false);
		assertTrue(modern.meta().inventory().handActive());

		User legacy = user(47);
		held(legacy, Material.DIAMOND_SWORD);
		legacy.meta().punishment().timeLastBlockCancel = System.currentTimeMillis();
		assertTrue(receive(legacy, USE_ITEM_ON, use(47, PlayerHand.MAIN_HAND), false).isCancelled());
		assertFalse(legacy.meta().inventory().handActive());
	}

	@Test
	void canonicalReleaseAndDropsPreserveSlowdownGuards() {
		for (int protocol : new int[]{47, 107, 477, 759, 777}) {
			User user = user(protocol);
			held(user, Material.GOLDEN_APPLE);
			user.meta().inventory().activateHand(PlayerHand.MAIN_HAND);
			user.meta().inventory().clearItemUseTickTransitions();
			MutableBlockDigPacket dig = WirePackets.createPlayPacket(BLOCK_DIG, protocol);
			dig.setAction(PlayerDigAction.RELEASE_USE_ITEM);
			dig.setBlockPosition(new BlockPosition(1, 0, 0));
			dig.setDirection(Direction.DOWN);
			receive(user, BLOCK_DIG, dig, false);
			assertTrue(user.meta().inventory().handActive());
			assertTrue(user.meta().inventory().suspiciousNoSlowdownPacketPatternThisTick());
			dig.setBlockPosition(BlockPosition.ORIGIN);
			dig.setDirection(Direction.UP);
			receive(user, BLOCK_DIG, dig, false);
			assertTrue(user.meta().inventory().handActive());
			dig.setDirection(Direction.DOWN);
			receive(user, BLOCK_DIG, dig, true);
			assertTrue(user.meta().inventory().handActive());
			receive(user, BLOCK_DIG, dig, false);
			assertFalse(user.meta().inventory().handActive());
			for (PlayerDigAction action : new PlayerDigAction[]{PlayerDigAction.DROP_ITEM, PlayerDigAction.DROP_ALL_ITEMS}) {
				user.meta().inventory().activateHand(PlayerHand.MAIN_HAND);
				dig.setAction(action);
				receive(user, BLOCK_DIG, dig, false);
				assertFalse(user.meta().inventory().handActive());
			}
		}
	}

	@Test
	void slotSwitchesAndTickEndKeepTheirStateBoundaries() {
		User user = user(777);
		held(user, Material.GOLDEN_APPLE);
		user.player().getInventory().setItem(1, null);
		user.meta().inventory().activateHand(PlayerHand.MAIN_HAND);
		receive(user, HELD_ITEM_SLOT_IN, new HeldItemSlotWirePacket(1), true);
		assertEquals(0, user.meta().inventory().handSlot());
		receive(user, HELD_ITEM_SLOT_IN, new HeldItemSlotWirePacket(-1), false);
		receive(user, HELD_ITEM_SLOT_IN, new HeldItemSlotWirePacket(36), false);
		assertEquals(0, user.meta().inventory().handSlot());
		receive(user, HELD_ITEM_SLOT_IN, new HeldItemSlotWirePacket(1), false);
		assertEquals(1, user.meta().inventory().handSlot());
		assertFalse(user.meta().inventory().handActive());

		user.meta().inventory().recordPreviousHeldItemForReach(new org.bukkit.inventory.ItemStack(Material.IRON_SPEAR));
		PacketContract end = roundTrip(SERVERBOUND, CLIENT_TICK_END, 777, out -> {});
		receive(user, CLIENT_TICK_END, end, true);
		assertNotNull(user.meta().inventory().previousSpearThisTick());
		receive(user, CLIENT_TICK_END, end, false);
		assertNull(user.meta().inventory().previousSpearThisTick());
	}

	@Test
	void serverSlotSwitchWaitsForNativeAcknowledgement() {
		for (int protocol : new int[]{47, 107, 777}) {
			User user = user(protocol);
			held(user, Material.GOLDEN_APPLE);
			PacketContract slot = new CarriedItemSlotWirePacket(2);
			assertTrue(send(user, HELD_ITEM_SLOT_OUT, slot, true).isEmpty());
			List<TypedPacketContract> sent = send(user, HELD_ITEM_SLOT_OUT, slot, false);
			assertEquals(0, user.meta().inventory().handSlot());
			acknowledge(user, sent);
			assertEquals(2, user.meta().inventory().handSlot());
		}
	}

	@Test
	void activeItemReplacementWaitsForDeliveryAndPreservesVersionRules() {
		for (int protocol : new int[]{47, 107, 393, 477, 766, 777}) {
			for (PacketType.Server type : new PacketType.Server[]{SET_SLOT, WINDOW_ITEMS}) {
				User user = user(protocol);
				held(user, Material.GOLDEN_APPLE);
				user.meta().inventory().activateHand(PlayerHand.MAIN_HAND);
				ItemStack replacement = new ItemStack(MinecraftKey.withDefaultNamespace("golden_apple"), 1, protocol);
				PacketContract packet = inventoryPacket(protocol, type, 0, 36, replacement);
				assertTrue(send(user, type, packet, true).isEmpty());
				List<TypedPacketContract> sent = send(user, type, packet, false);
				assertTrue(user.meta().inventory().handActive());
				acknowledge(user, sent);
				assertEquals(protocol >= 477, user.meta().inventory().handActive(), "protocol " + protocol);
				if (protocol >= 477) {
					sent = send(user, type, inventoryPacket(protocol, type, 0, 36, null), false);
					assertTrue(user.meta().inventory().handActive());
					acknowledge(user, sent);
					assertFalse(user.meta().inventory().handActive());
				}
			}
		}
	}

	@Test
	void unrelatedAndStaleInventoryUpdatesCannotStopANewerUse() {
		User user = user(777);
		user.player().getInventory().setItemInOffHand(new org.bukkit.inventory.ItemStack(Material.SHIELD));
		user.meta().inventory().activateHand(PlayerHand.OFF_HAND);
		assertEquals(1, send(user, SET_SLOT, inventoryPacket(777, SET_SLOT, 1, 45, null), false).size());
		assertEquals(1, send(user, SET_SLOT, inventoryPacket(777, SET_SLOT, 0, 36, null), false).size());
		List<TypedPacketContract> sent = send(user, SET_SLOT, inventoryPacket(777, SET_SLOT, 0, 45, null), false);
		user.meta().inventory().deactivateHand();
		user.meta().inventory().activateHand(PlayerHand.OFF_HAND);
		acknowledge(user, sent);
		assertTrue(user.meta().inventory().handActive());
		acknowledge(user, send(user, SET_SLOT, inventoryPacket(777, SET_SLOT, 0, 45, null), false));
		assertFalse(user.meta().inventory().handActive());
	}

	private User user(int protocol) {
		var world = FakeWorldFactory.createWorld((name, args) -> null);
		UUID id = UUID.randomUUID();
		Player player = FakePlayerFactory.createPlayer((name, args) -> switch (name) {
			case "getWorld" -> world;
			case "getLocation" -> new Location(world, 0, 64, 0);
			case "getUniqueId" -> id;
			default -> null;
		});
		User user = UserFactory.createTestUserFor(player, protocol);
		UserRepository.manuallyRegisterUser(player, user);
		players.add(player);
		return user;
	}

	private static void held(User user, Material type) {
		user.player().getInventory().setItem(0, new org.bukkit.inventory.ItemStack(type));
		user.meta().inventory().setHeldItemSlot(0);
	}

	private static PacketReceiveEvent receive(User user, PacketType.Client type, PacketContract packet, boolean cancelled) {
		PacketReceiveEvent event = new PacketReceiveEvent(user, type, packet);
		event.setCancelled(cancelled);
		PacketEventBus.dispatch(event);
		return event;
	}

	private static List<TypedPacketContract> send(User user, PacketType.Server type, PacketContract packet, boolean cancelled) {
		PacketSendEvent event = new PacketSendEvent(user, type, packet);
		event.setCancelled(cancelled);
		return PacketEventBus.dispatch(event);
	}

	private static void acknowledge(User user, List<TypedPacketContract> sent) {
		assertEquals(2, sent.size(), "Item update followed by exactly one acknowledgement request");
		PacketReceiveEvent reply = NativeFeedbackTestSupport.acknowledgement(user, sent.get(1));
		PacketEventBus.dispatch(reply);
		assertTrue(reply.isCancelled());
	}

	private static PacketContract use(int protocol, PlayerHand hand) {
		return roundTrip(SERVERBOUND, protocol < 107 ? USE_ITEM_ON : USE_ITEM, protocol, out -> {
			if (protocol < 107) {
				out.writeLong(-1).writeByte(255).writeShort(-1).writeByte(0).writeByte(0).writeByte(0);
			} else {
				VARINT.encode(out, hand.ordinal());
				if (protocol >= 759) VARINT.encode(out, 12);
				if (protocol >= 767) out.writeFloat(0).writeFloat(0);
			}
		});
	}

	private static PacketContract inventoryPacket(int protocol, PacketType.Server type, int container, int slot, ItemStack item) {
		return roundTrip(CLIENTBOUND, type, protocol, out -> {
			if (protocol < 768) out.writeByte(container);
			else VARINT.encode(out, container);
			if (protocol >= 756) VARINT.encode(out, 7);
			if (type == SET_SLOT) {
				out.writeShort(slot);
				ItemStack.codecFor(protocol).encode(out, item);
			} else {
				if (protocol < 756) out.writeShort(46);
				else VARINT.encode(out, 46);
				for (int i = 0; i < 46; i++) ItemStack.codecFor(protocol).encode(out, i == slot ? item : null);
				if (protocol >= 756) ItemStack.codecFor(protocol).encode(out, null);
			}
		});
	}
}
