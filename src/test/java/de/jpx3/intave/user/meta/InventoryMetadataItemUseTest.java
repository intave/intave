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

package de.jpx3.intave.user.meta;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryMetadataItemUseTest {
  private Player registeredPlayer;

  @BeforeEach
  void setServerVersion() {
    MinecraftVersion.setCurrent(new MinecraftVersion("1.8.9"));
  }

  @AfterEach
  void tearDown() {
    if (registeredPlayer != null) {
      UserRepository.unregisterUser(registeredPlayer);
    }
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
  }

  @Test
  void useAfterSlotSwitchRemainsActive() {
    User user = activeItemUseUser(Material.GOLDEN_APPLE, Material.DIAMOND_SWORD);
    InventoryMetadata inventory = user.meta().inventory();

    inventory.setHeldItemSlot(1);
    inventory.activateHand();
    recordSlotSwitch(inventory, 1);
    inventory.updateSlotSwitch();

    assertTrue(inventory.handActive());
    assertEquals(Material.DIAMOND_SWORD, inventory.activeItemType());
    assertFalse(inventory.releaseItemNextTick);
    assertEquals(Material.AIR, inventory.releaseItemType);
  }

  @Test
  void useAfterSlotSwitchDoesNotCancelPendingEnforcement() {
    User user = activeItemUseUser(Material.GOLDEN_APPLE, Material.DIAMOND_SWORD);
    InventoryMetadata inventory = user.meta().inventory();

    inventory.releaseItemNextTick();
    inventory.setHeldItemSlot(1);
    inventory.activateHand();
    recordSlotSwitch(inventory, 1);
    inventory.updateSlotSwitch();

    assertTrue(inventory.handActive());
    assertEquals(Material.DIAMOND_SWORD, inventory.activeItemType());
    assertTrue(inventory.releaseItemNextTick);
    assertEquals(Material.GOLDEN_APPLE, inventory.releaseItemType);
  }

  @Test
  void switchingToABowDoesNotRelabelTheOldReleaseAsABowRelease() {
    User user = activeItemUseUser(Material.DIAMOND_SWORD, Material.BOW);
    InventoryMetadata inventory = user.meta().inventory();

    inventory.setHeldItemSlot(1);
    inventory.releaseItemNextTick();

    assertEquals(Material.DIAMOND_SWORD, inventory.releaseItemType);
  }

  @Test
  void sameMaterialInAnotherSlotStillStartsANewUseSession() {
    User user = activeItemUseUser(Material.BOW, Material.BOW);
    InventoryMetadata inventory = user.meta().inventory();

    inventory.setHeldItemSlot(1);
    inventory.activateHand();
    recordSlotSwitch(inventory, 1);
    inventory.updateSlotSwitch();

    assertTrue(inventory.handActive());
    assertEquals(Material.BOW, inventory.activeItemType());
    assertFalse(inventory.releaseItemNextTick);
  }

  @Test
  void slotSwitchWithoutNewItemUseEndsTrackingWithoutForcingRelease() {
    User user = activeItemUseUser(Material.BOW, Material.DIAMOND_SWORD);
    InventoryMetadata inventory = user.meta().inventory();

    inventory.setHeldItemSlot(1);
    recordSlotSwitch(inventory, 1);
    inventory.updateSlotSwitch();

    assertFalse(inventory.handActive());
    assertFalse(inventory.releaseItemNextTick);
    assertEquals(Material.AIR, inventory.releaseItemType);
  }

  @Test
  void repeatedUsePacketForTheSameSessionDoesNotCancelEnforcement() {
    User user = activeItemUseUser(Material.BOW, Material.DIAMOND_SWORD);
    InventoryMetadata inventory = user.meta().inventory();

    inventory.releaseItemNextTick();
    inventory.activateHand();

    assertTrue(inventory.releaseItemNextTick);
    assertEquals(Material.BOW, inventory.releaseItemType);
  }

  private void recordSlotSwitch(InventoryMetadata inventory, int slot) {
    inventory.slotSwitchData = new InventoryMetadata.SlotSwitchData(slot);
  }

  private User activeItemUseUser(Material firstItem, Material secondItem) {
    var world = FakeWorldFactory.createWorld((method, arguments) -> switch (method) {
      case "isChunkLoaded", "isChunkInUse" -> true;
      case "isThundering", "hasStorm" -> false;
      default -> null;
    });
    var location = new Location(world, 0.0, 64.0, 0.0);
    Player player = FakePlayerFactory.createPlayer((method, arguments) -> switch (method) {
      case "getWorld" -> world;
      case "getLocation" -> location;
      default -> null;
    });
    player.getInventory().setItem(0, new ItemStack(firstItem));
    player.getInventory().setItem(1, new ItemStack(secondItem));
    player.getInventory().setItem(8, new ItemStack(Material.ARROW));
    User user = UserFactory.createTestUserFor(player, 47);
    this.registeredPlayer = player;
    UserRepository.manuallyRegisterUser(player, user);
    user.meta().inventory().activateHand();
    return user;
  }
}
