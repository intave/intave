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
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlayerHandTrackerItemUseTest {
  @BeforeAll
  static void setUpItems() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
    ItemProperties.setup();
  }

  @Test
  void legacySwordUsePacketActivatesTrackedHand() {
    User user = legacyUser();
    user.player().getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
    user.meta().inventory().setHeldItemSlot(0);

    TestCancellable cancellable = new TestCancellable();
    new PlayerHandTracker().receiveItemUseRequest(
      cancellable, user, PlayerHand.MAIN_HAND
    );

    assertTrue(user.meta().inventory().handActive());
  }

  @Test
  void forcedLegacyReleaseBecomesUncertainAfterTheMovementTick() {
    User user = legacyUser();
    user.player().getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
    user.meta().inventory().setHeldItemSlot(0);
    PlayerHandTracker tracker = new PlayerHandTracker();
    TestCancellable cancellable = new TestCancellable();

    tracker.receiveItemUseRequest(cancellable, user, PlayerHand.MAIN_HAND);
    user.meta().inventory().tickComplete();
    user.meta().inventory().releaseItemNextTick();

    assertTrue(user.meta().inventory().releaseItemNextTick);

    assertEquals(
      Material.DIAMOND_SWORD,
      user.meta().inventory().deferScheduledItemReleaseUntilTickEnd(true)
    );

    assertTrue(user.meta().inventory().handActive());
    assertFalse(user.meta().movement().legacyItemUseReleaseUncertain());

    user.meta().inventory().tickComplete();

    assertFalse(user.meta().inventory().releaseItemNextTick);
    assertFalse(user.meta().inventory().handActive());
    assertTrue(user.meta().movement().legacyItemUseReleaseUncertain());
  }

  @Test
  void bareFlyingPacketKeepsScheduledLegacyReleasePending() {
    User user = legacyUser();
    user.player().getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
    user.meta().inventory().setHeldItemSlot(0);
    new PlayerHandTracker().receiveItemUseRequest(
      new TestCancellable(), user, PlayerHand.MAIN_HAND
    );
    user.meta().inventory().releaseItemNextTick();

    assertNull(
      user.meta().inventory().deferScheduledItemReleaseUntilTickEnd(false)
    );
    user.meta().inventory().tickComplete();

    assertTrue(user.meta().inventory().releaseItemNextTick);
    assertTrue(user.meta().inventory().handActive());

    assertEquals(
      Material.DIAMOND_SWORD,
      user.meta().inventory().deferScheduledItemReleaseUntilTickEnd(true)
    );
    user.meta().inventory().tickComplete();

    assertFalse(user.meta().inventory().releaseItemNextTick);
    assertFalse(user.meta().inventory().handActive());
  }

  @Test
  void legacySlotSwitchFollowedByUsePacketStartsTheNewItemUse() {
    User user = legacyUser();
    user.player().getInventory().setItem(0, new ItemStack(Material.GOLDEN_APPLE));
    user.player().getInventory().setItem(1, new ItemStack(Material.DIAMOND_SWORD));
    user.meta().inventory().setHeldItemSlot(0);
    PlayerHandTracker tracker = new PlayerHandTracker();
    TestCancellable cancellable = new TestCancellable();

    tracker.receiveItemUseRequest(cancellable, user, PlayerHand.MAIN_HAND);
    user.meta().inventory().tickComplete();
    user.meta().inventory().receiveHeldItemSlot(
      1, user.player().getInventory().getItem(1)
    );

    tracker.receiveItemUseRequest(cancellable, user, PlayerHand.MAIN_HAND);

    assertFalse(user.meta().inventory().releaseItemNextTick);
    assertTrue(user.meta().inventory().handActive());
    assertEquals(Material.DIAMOND_SWORD, user.meta().inventory().activeItemType());
    assertEquals(0, user.meta().inventory().handActiveTicks);

    user.meta().inventory().updateSlotSwitch();
    user.meta().inventory().tickComplete();

    assertTrue(user.meta().inventory().handActive());
    assertFalse(user.meta().movement().legacyItemUseReleaseUncertain());
  }

  @Test
  void minecraft18SlotSwitchWhileWalkingDoesNotScheduleSyntheticReset() {
    User user = legacyUser();
    user.player().getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
    user.player().getInventory().setItem(1, new ItemStack(Material.IRON_SWORD));
    user.meta().inventory().setHeldItemSlot(0);
    PlayerHandTracker tracker = new PlayerHandTracker();
    tracker.receiveItemUseRequest(
      new TestCancellable(), user, PlayerHand.MAIN_HAND
    );
    user.meta().inventory().tickComplete();

    user.meta().inventory().receiveHeldItemSlot(
      1, user.player().getInventory().getItem(1)
    );

    // Even a small W movement carries position. It must not turn the already
    // observed slot switch into a synthetic RELEASE_USE_ITEM/updateInventory.
    assertFalse(user.meta().inventory().handActive());
    assertFalse(user.meta().inventory().releaseItemNextTick);
    assertNull(user.meta().inventory().deferScheduledItemReleaseUntilTickEnd(true));
  }

  @Test
  void usePacketStartsAgainAfterForcedReleaseWasApplied() {
    User user = legacyUser();
    user.player().getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
    user.meta().inventory().setHeldItemSlot(0);
    user.meta().movement().setLegacyItemUseForcedReleaseApplied(true);

    new PlayerHandTracker().receiveItemUseRequest(
      new TestCancellable(), user, PlayerHand.MAIN_HAND
    );

    assertTrue(user.meta().inventory().handActive());
    assertFalse(user.meta().movement().legacyItemUseForcedReleaseApplied());
  }

  @Test
  void minecraft19UsePacketOnlyActivatesTheRequestedHand() {
    User user = user(ProtocolMetadata.VER_1_9);
    user.player().getInventory().setItem(0, new ItemStack(Material.STONE));
    user.player().getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
    user.meta().inventory().setHeldItemSlot(0);
    PlayerHandTracker tracker = new PlayerHandTracker();

    tracker.receiveItemUseRequest(
      new TestCancellable(), user, PlayerHand.MAIN_HAND
    );
    assertFalse(user.meta().inventory().handActive());

    tracker.receiveItemUseRequest(
      new TestCancellable(), user, PlayerHand.OFF_HAND
    );
    assertTrue(user.meta().inventory().handActive());
    assertEquals(PlayerHand.OFF_HAND, user.meta().inventory().activeHand());
    assertEquals(Material.SHIELD, user.meta().inventory().activeItemType());
  }

  @Test
  void minecraft19SecondUsePacketCannotReplaceTheActiveHand() {
    User user = user(ProtocolMetadata.VER_1_9);
    user.player().getInventory().setItem(0, new ItemStack(Material.BOW));
    user.player().getInventory().setItem(1, new ItemStack(Material.ARROW));
    user.player().getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
    user.meta().inventory().setHeldItemSlot(0);
    PlayerHandTracker tracker = new PlayerHandTracker();

    tracker.receiveItemUseRequest(
      new TestCancellable(), user, PlayerHand.OFF_HAND
    );
    tracker.receiveItemUseRequest(
      new TestCancellable(), user, PlayerHand.MAIN_HAND
    );

    assertEquals(PlayerHand.OFF_HAND, user.meta().inventory().activeHand());
    assertEquals(Material.SHIELD, user.meta().inventory().activeItemType());
  }

  @Test
  void repeatedUsableItemRequestPreservesPendingSlotSwitchGuard() {
    User user = user(ProtocolMetadata.VER_26_1_1);
    user.player().getInventory().setItem(0, new ItemStack(Material.GOLDEN_APPLE));
    user.meta().inventory().setHeldItemSlot(0);
    PlayerHandTracker tracker = new PlayerHandTracker();
    TestCancellable cancellable = new TestCancellable();

    tracker.receiveItemUseRequest(
      cancellable, user, PlayerHand.MAIN_HAND
    );
    assertFalse(user.meta().inventory().repeatedItemUseRequestThisTick());

    tracker.receiveItemUseRequest(
      cancellable, user, PlayerHand.MAIN_HAND
    );
    assertFalse(user.meta().inventory().repeatedItemUseRequestThisTick());

    user.meta().inventory().tickComplete();
    user.meta().movement().setLegacyItemUseSlotSwitchPending(true);
    tracker.receiveItemUseRequest(
      cancellable, user, PlayerHand.MAIN_HAND
    );
    assertTrue(user.meta().inventory().repeatedItemUseRequestThisTick());
    assertTrue(user.meta().movement().legacyItemUseSlotSwitchPending());

    user.meta().inventory().clearItemUseTickTransitions();
    assertFalse(user.meta().inventory().repeatedItemUseRequestThisTick());
  }

  @Test
  void heldItemBurstReturningToOriginalPreservesItemUseForPhysics() {
    User user = user(ProtocolMetadata.VER_26_1_1);
    user.player().getInventory().setItem(0, new ItemStack(Material.GOLDEN_APPLE));
    user.player().getInventory().setItem(1, new ItemStack(Material.DIAMOND_SWORD));
    user.player().getInventory().setItem(2, new ItemStack(Material.IRON_SWORD));
    user.meta().inventory().setHeldItemSlot(0);
    PlayerHandTracker tracker = new PlayerHandTracker();

    tracker.receiveItemUseRequest(
      new TestCancellable(), user, PlayerHand.MAIN_HAND
    );
    user.meta().inventory().tickComplete();

    user.meta().inventory().receiveHeldItemSlot(
      1, user.player().getInventory().getItem(1)
    );
    user.meta().inventory().receiveHeldItemSlot(
      2, user.player().getInventory().getItem(2)
    );
    user.meta().inventory().receiveHeldItemSlot(
      0, user.player().getInventory().getItem(0)
    );

    assertTrue(user.meta().inventory().handActive());
    assertEquals(Material.GOLDEN_APPLE, user.meta().inventory().activeItemType());
    assertEquals(1, user.meta().inventory().handActiveTicks);
    assertTrue(user.meta().movement().legacyItemUseSlotSwitchPending());
  }

  @Test
  void modernInvalidHandDoesNotStartGuessedItemUse() {
    User user = user(ProtocolMetadata.VER_26_1_1);
    user.player().getInventory().setItem(0, new ItemStack(Material.GOLDEN_APPLE));
    user.meta().inventory().setHeldItemSlot(0);

    new PlayerHandTracker().receiveItemUseRequest(
      new TestCancellable(), user, null
    );

    assertFalse(user.meta().inventory().handActive());
  }

  @Test
  void translatedMinecraft19SwordBlockStillStartsUsingTheMainHand() {
    MinecraftVersion serverVersion = MinecraftVersion.current();
    try {
      MinecraftVersion.setCurrent(MinecraftVersions.VER1_8_0);
      User user = user(ProtocolMetadata.VER_1_9);
      user.player().getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
      user.meta().inventory().setHeldItemSlot(0);

      new PlayerHandTracker().receiveItemUseRequest(
        new TestCancellable(), user, PlayerHand.MAIN_HAND
      );

      assertTrue(user.meta().protocol().viaVersionShieldBlockReplacement());
      assertTrue(user.meta().inventory().handActive());
      assertEquals(Material.DIAMOND_SWORD, user.meta().inventory().activeItemType());
    } finally {
      MinecraftVersion.setCurrent(serverVersion);
    }
  }

  @Test
  void minecraft19SlotSwitchStopsMainHandUseBeforeMovement() {
    User user = user(ProtocolMetadata.VER_1_9);
    user.player().getInventory().setItem(0, new ItemStack(Material.BOW));
    user.player().getInventory().setItem(1, new ItemStack(Material.SHIELD));
    user.player().getInventory().setItem(2, new ItemStack(Material.ARROW));
    user.meta().inventory().setHeldItemSlot(0);
    PlayerHandTracker tracker = new PlayerHandTracker();
    tracker.receiveItemUseRequest(
      new TestCancellable(), user, PlayerHand.MAIN_HAND
    );

    user.meta().inventory().receiveHeldItemSlot(
      1, user.player().getInventory().getItem(1)
    );

    assertFalse(user.meta().inventory().handActive());
    assertFalse(user.meta().inventory().releaseItemNextTick);
  }

  @Test
  void minecraft19SlotSwitchDoesNotStopOffhandUse() {
    User user = user(ProtocolMetadata.VER_1_9);
    user.player().getInventory().setItem(0, new ItemStack(Material.BOW));
    user.player().getInventory().setItem(1, new ItemStack(Material.BOW));
    user.player().getInventory().setItem(2, new ItemStack(Material.ARROW));
    user.player().getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
    user.meta().inventory().setHeldItemSlot(0);
    PlayerHandTracker tracker = new PlayerHandTracker();
    tracker.receiveItemUseRequest(
      new TestCancellable(), user, PlayerHand.OFF_HAND
    );

    user.meta().inventory().receiveHeldItemSlot(
      1, user.player().getInventory().getItem(1)
    );
    user.meta().inventory().updateSlotSwitch();

    assertTrue(user.meta().inventory().handActive());
    assertEquals(PlayerHand.OFF_HAND, user.meta().inventory().activeHand());
  }

  @Test
  void sameTypeSlotSwitchPreservesModernUseUntilClientReceivesTheStop() {
    for (int protocol : new int[] {
      ProtocolMetadata.VER_1_13, ProtocolMetadata.VER_1_14,
      ProtocolMetadata.VER_1_20_5, ProtocolMetadata.VER_26_3
    }) {
      User user = user(protocol);
      user.player().getInventory().setItem(0, new ItemStack(Material.SHIELD));
      user.player().getInventory().setItem(1, new ItemStack(Material.SHIELD));
      InventoryMetadata inventory = user.meta().inventory();
      inventory.setHeldItemSlot(0);
      inventory.activateHand(PlayerHand.MAIN_HAND);
      long sequence = inventory.activeItemUseSequence();
      inventory.applyClientItemUseFlags(sequence, true);
      inventory.tickComplete();

      inventory.receiveHeldItemSlot(1, user.player().getInventory().getItem(1));
      inventory.updateSlotSwitch();
      for (int tick = 0; tick < 4; tick++) {
        inventory.tickComplete();
        assertEquals(protocol >= ProtocolMetadata.VER_1_14, inventory.handActive(),
          "protocol " + protocol + ", tick " + tick);
      }
      if (protocol >= ProtocolMetadata.VER_1_14) {
        assertEquals(sequence, inventory.activeItemUseSequence());
        assertEquals(5, inventory.handActiveTicks);
        assertFalse(inventory.itemUseInterruptedBySlotSwitchThisTick());
        assertEquals(37, inventory.activeClientInventorySlot(0));
        inventory.applyClientItemUseFlags(sequence, false);
        assertFalse(inventory.handActive());
      }
    }
  }

  @Test
  void modernSlotSwitchStillStopsUseForDifferentOrEmptyStacks() {
    for (ItemStack replacement : new ItemStack[] {
      new ItemStack(Material.STONE), null, new ItemStack(Material.SHIELD, 0)
    }) {
      User user = user(ProtocolMetadata.VER_1_20_5);
      user.player().getInventory().setItem(0, new ItemStack(Material.SHIELD));
      InventoryMetadata inventory = user.meta().inventory();
      inventory.setHeldItemSlot(0);
      inventory.activateHand(PlayerHand.MAIN_HAND);

      inventory.receiveHeldItemSlot(1, replacement);

      assertFalse(inventory.handActive());
    }
  }

  @Test
  void minecraft19ClientboundReplacementStopsUseEvenForTheSameItemType() {
    User user = user(ProtocolMetadata.VER_1_9);
    user.player().getInventory().setItem(0, new ItemStack(Material.BOW));
    user.player().getInventory().setItem(1, new ItemStack(Material.ARROW));
    user.meta().inventory().setHeldItemSlot(0);
    user.meta().inventory().activateHand(PlayerHand.MAIN_HAND);
    long useSequence = user.meta().inventory().activeItemUseSequence();

    assertEquals(36, user.meta().inventory().activeClientInventorySlot(0));
    user.meta().inventory().applyClientActiveItemReplacement(
      useSequence, Material.BOW
    );

    assertFalse(user.meta().inventory().handActive());
  }

  @Test
  void minecraft18ClientboundReplacementStopsUseEvenForTheSameItemType() {
    User user = legacyUser();
    user.player().getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD));
    user.meta().inventory().setHeldItemSlot(0);
    user.meta().inventory().activateHand(PlayerHand.MAIN_HAND);
    long useSequence = user.meta().inventory().activeItemUseSequence();

    assertEquals(36, user.meta().inventory().activeClientInventorySlot(0));
    assertTrue(user.meta().inventory().applyClientActiveItemReplacement(
      useSequence, Material.WOODEN_SWORD
    ));

    assertFalse(user.meta().inventory().handActive());
  }

  @Test
  void minecraft114ClientboundReplacementKeepsUseForTheSameItemType() {
    User user = user(ProtocolMetadata.VER_1_14);
    user.player().getInventory().setItem(0, new ItemStack(Material.BOW));
    user.player().getInventory().setItem(1, new ItemStack(Material.ARROW));
    user.meta().inventory().setHeldItemSlot(0);
    user.meta().inventory().activateHand(PlayerHand.MAIN_HAND);
    long useSequence = user.meta().inventory().activeItemUseSequence();

    user.meta().inventory().applyClientActiveItemReplacement(
      useSequence, Material.BOW
    );

    assertTrue(user.meta().inventory().handActive());
  }

  @Test
  void minecraft114ClientboundReplacementStopsUseForAnotherItemType() {
    User user = user(ProtocolMetadata.VER_1_14);
    user.player().getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
    user.meta().inventory().activateHand(PlayerHand.OFF_HAND);
    long useSequence = user.meta().inventory().activeItemUseSequence();

    assertEquals(45, user.meta().inventory().activeClientInventorySlot(0));
    assertEquals(40, user.meta().inventory().activeClientInventorySlot(-2));
    user.meta().inventory().applyClientActiveItemReplacement(
      useSequence, Material.STONE
    );

    assertFalse(user.meta().inventory().handActive());
  }

  @Test
  void staleClientboundReplacementCannotStopANewerUse() {
    User user = user(ProtocolMetadata.VER_1_14);
    user.player().getInventory().setItem(0, new ItemStack(Material.BOW));
    user.player().getInventory().setItem(1, new ItemStack(Material.ARROW));
    user.meta().inventory().setHeldItemSlot(0);
    user.meta().inventory().activateHand(PlayerHand.MAIN_HAND);
    long oldUseSequence = user.meta().inventory().activeItemUseSequence();
    user.meta().inventory().deactivateHand();
    user.meta().inventory().activateHand(PlayerHand.MAIN_HAND);

    user.meta().inventory().applyClientActiveItemReplacement(
      oldUseSequence, Material.STONE
    );

    assertTrue(user.meta().inventory().handActive());
  }

  @Test
  void modernInactiveMetadataOnlyEndsAConfirmedUse() {
    User user = user(ProtocolMetadata.VER_1_14);
    user.player().getInventory().setItem(0, new ItemStack(Material.BOW));
    user.player().getInventory().setItem(1, new ItemStack(Material.ARROW));
    user.meta().inventory().setHeldItemSlot(0);
    user.meta().inventory().activateHand(PlayerHand.MAIN_HAND);
    long useSequence = user.meta().inventory().activeItemUseSequence();

    user.meta().inventory().applyClientItemUseFlags(useSequence, false);
    assertTrue(user.meta().inventory().handActive());

    user.meta().inventory().applyClientItemUseFlags(useSequence, true);
    user.meta().inventory().applyClientItemUseFlags(useSequence, false);
    assertFalse(user.meta().inventory().handActive());
  }

  @Test
  void modernConsumeEndsWhenItsInventoryUpdateReachesTheClient() {
    User user = user(ProtocolMetadata.VER_1_14);
    user.player().getInventory().setItem(0, new ItemStack(Material.GOLDEN_APPLE));
    user.meta().inventory().setHeldItemSlot(0);
    user.meta().inventory().activateHand(PlayerHand.MAIN_HAND);
    long useSequence = user.meta().inventory().activeItemUseSequence();

    user.meta().inventory().markServerItemUseCompleted();
    assertTrue(user.meta().inventory().handActive());

    user.meta().inventory().applyClientActiveItemReplacement(
      useSequence, Material.GOLDEN_APPLE
    );
    assertFalse(user.meta().inventory().handActive());
  }

  @Test
  void onlyBrushStartsContinuousUseFromUseOnBlock() {
    User user = user(ProtocolMetadata.VER_1_19_4);
    user.player().getInventory().setItem(0, new ItemStack(Material.BOW));
    user.player().getInventory().setItem(1, new ItemStack(Material.ARROW));
    user.meta().inventory().setHeldItemSlot(0);
    PlayerHandTracker tracker = new PlayerHandTracker();

    tracker.receiveItemUseRequest(
      new TestCancellable(), user, PlayerHand.MAIN_HAND, true
    );
    assertFalse(user.meta().inventory().handActive());

    user.player().getInventory().setItem(0, new ItemStack(Material.BRUSH));
    tracker.receiveItemUseRequest(
      new TestCancellable(), user, PlayerHand.MAIN_HAND, true
    );
    assertTrue(user.meta().inventory().handActive());
    assertEquals(Material.BRUSH, user.meta().inventory().activeItemType());
  }

  private static User legacyUser() {
    return user(ProtocolMetadata.VER_1_8);
  }

  private static User user(int protocolVersion) {
    User user = UserFactory.createTestUserFor(
      FakePlayerFactory.createPlayer((methodName, arguments) ->
        methodName.equals("getLocation")
          ? new Location(null, 0.0D, 64.0D, 0.0D)
          : null
      ),
      protocolVersion
    );
    UserRepository.manuallyRegisterUser(user.player(), user);
    return user;
  }

  private static final class TestCancellable implements Cancellable {
    private boolean cancelled;

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
      cancelled = cancel;
    }
  }
}
