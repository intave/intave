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
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryMetadataItemUseTest {
  @BeforeEach
  void setServerVersion() {
    MinecraftVersion.setCurrent(new MinecraftVersion("1.8.9"));
  }

  @Test
  void immediateUseAfterSlotSwitchSupersedesThePendingRelease() {
    User user = itemUseUser(Material.BOW, Material.DIAMOND_SWORD);
    InventoryMetadata inventory = user.meta().inventory();
    restoreActiveItem(inventory, Material.BOW);

    inventory.setHeldItemSlot(1);
    inventory.releaseItemNextTick();
    inventory.activateHand();

    assertEquals(Material.DIAMOND_SWORD, inventory.activeItemType());
    assertFalse(inventory.releaseItemNextTick);
    assertEquals(Material.AIR, inventory.releaseItemType);
  }

  @Test
  void switchingToABowDoesNotRelabelTheOldReleaseAsABowRelease() {
    User user = itemUseUser(Material.DIAMOND_SWORD, Material.BOW);
    InventoryMetadata inventory = user.meta().inventory();
    restoreActiveItem(inventory, Material.DIAMOND_SWORD);

    inventory.setHeldItemSlot(1);
    inventory.releaseItemNextTick();

    assertEquals(Material.DIAMOND_SWORD, inventory.releaseItemType);
  }

  @Test
  void sameMaterialInAnotherSlotStillStartsANewUseSession() {
    User user = itemUseUser(Material.BOW, Material.BOW);
    InventoryMetadata inventory = user.meta().inventory();
    restoreActiveItem(inventory, Material.BOW);

    inventory.setHeldItemSlot(1);
    inventory.releaseItemNextTick();
    inventory.activateHand();

    assertEquals(Material.BOW, inventory.activeItemType());
    assertFalse(inventory.releaseItemNextTick);
  }

  @Test
  void slotSwitchWithoutNewItemUseKeepsThePendingRelease() {
    User user = itemUseUser(Material.BOW, Material.DIAMOND_SWORD);
    InventoryMetadata inventory = user.meta().inventory();
    restoreActiveItem(inventory, Material.BOW);

    inventory.setHeldItemSlot(1);
    inventory.releaseItemNextTick();
    inventory.slotSwitchData = new InventoryMetadata.SlotSwitchData(
      1, user.player().getInventory().getItem(1)
    );
    inventory.updateSlotSwitch();

    assertTrue(inventory.releaseItemNextTick);
    assertEquals(Material.BOW, inventory.releaseItemType);
  }

  @Test
  void repeatedUsePacketForTheSameSessionDoesNotCancelEnforcement() {
    User user = itemUseUser(Material.BOW, Material.DIAMOND_SWORD);
    InventoryMetadata inventory = user.meta().inventory();
    restoreActiveItem(inventory, Material.BOW);

    inventory.releaseItemNextTick();
    inventory.activateHand();

    assertTrue(inventory.releaseItemNextTick);
    assertEquals(Material.BOW, inventory.releaseItemType);
  }

  private static User itemUseUser(Material firstItem, Material secondItem) {
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
    User user = UserFactory.createTestUserFor(player, 47);
    UserRepository.manuallyRegisterUser(player, user);
    return user;
  }

  private static void restoreActiveItem(InventoryMetadata inventory, Material material) {
    inventory.restoreRecordedState(
      0, true, 4, 0, 0, false, material,
      false, false, Material.AIR, false, false
    );
  }
}
