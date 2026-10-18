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

package de.jpx3.intave.check.movement.physics.branch;


import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
final class UseItemBrancherTest {
  @BeforeAll
  static void setUpItems() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
    ItemProperties.setup();
  }

  @Test
  void minecraft18InactiveHandProducesOnlyInactiveMovement() {
    User user = minecraft18UserWithSwordInSlot(0);

    List<MovementSearchBranch> branches = branches(user, false);

    assertEquals(1, branches.size());
    assertFalse(branches.get(0).moveConfig().isHandActive());
  }

  @Test
  void disablingEnforcementAllowsUnslowedMovementWithoutConsumingASlotSwitch() {
    for (int protocol : new int[] {
      ProtocolMetadata.VER_1_8, ProtocolMetadata.VER_1_9,
      ProtocolMetadata.VER_1_14, ProtocolMetadata.VER_1_21_11
    }) {
      User user = protocol == ProtocolMetadata.VER_1_8
        ? minecraft18UserWithSwordInSlot(0) : modernUserWithBow(protocol);
      user.meta().inventory().activateHand(PlayerHand.MAIN_HAND);
      user.meta().inventory().tickComplete();
      SimulationEnvironment environment = environment(user);
      environment.setLegacyItemUseSlotSwitchPending(true);

      List<MovementSearchBranch> enforced = branches(user, true, environment, 1, 0);
      assertTrue(enforced.stream().allMatch(branch -> branch.moveConfig().isHandActive()));

      List<MovementSearchBranch> disabled = branches(user, true, environment, 1, 0, false);
      assertTrue(disabled.stream().anyMatch(branch -> branch.moveConfig().isHandActive()));
      MovementSearchBranch unslowed = disabled.stream()
        .filter(branch -> !branch.moveConfig().isHandActive()).findFirst().orElseThrow();
      assertTrue(unslowed.isSprinting());
      assertTrue(unslowed.modifiedImmutableView(environment).legacyItemUseSlotSwitchPending());
      assertTrue(user.meta().inventory().handActive());
    }
  }

  @Test
  void modernSameTypeSlotSwitchKeepsSlowedMovementBeyondTheFirstTick() {
    User user = modernUserWithBow(ProtocolMetadata.VER_1_20_5);
    user.player().getInventory().setItem(2, new ItemStack(Material.BOW));
    InventoryMetadata inventory = user.meta().inventory();
    inventory.activateHand(PlayerHand.MAIN_HAND);
    inventory.receiveHeldItemSlot(2, user.player().getInventory().getItem(2));
    inventory.updateSlotSwitch();

    for (int tick = 0; tick < 4; tick++) {
      inventory.tickComplete();
      assertTrue(branches(user, false, environment(user), 1, 0).stream()
        .anyMatch(branch -> branch.moveConfig().isHandActive()), "tick " + tick);
    }
  }

  @Test
  void minecraft18ForcedReleaseRemainsBranchableUntilMovementResolvesIt() {
    User user = minecraft18UserWithSwordInSlot(0);
    SimulationEnvironment environment = environment(user);
    environment.setLegacyItemUseReleaseUncertain(true);

    List<MovementSearchBranch> branches = branches(user, true, environment);

    assertEquals(2, branches.size());
    assertTrue(branches.get(0).moveConfig().isHandActive());
    assertFalse(branches.get(0).moveConfig().isSprinting());
    assertTrue(
      branches.get(0).modifiedImmutableView(environment)
        .legacyItemUseReleaseUncertain()
    );
    assertFalse(branches.get(1).moveConfig().isHandActive());
    assertFalse(
      branches.get(1).modifiedImmutableView(environment)
        .legacyItemUseReleaseUncertain()
    );
    assertTrue(
      branches.get(1).modifiedImmutableView(environment)
        .legacyItemUseForcedReleaseApplied()
    );

    branches.get(1).modifiedMutableView(environment).commitTo(environment);
    assertFalse(environment.legacyItemUseReleaseUncertain());
    assertTrue(environment.legacyItemUseForcedReleaseApplied());
  }

  @Test
  void minecraft18AppliedForcedReleaseOverridesStaleTrackedUse() {
    User user = minecraft18UserWithSwordInSlot(0);
    user.meta().inventory().activateHand();
    SimulationEnvironment environment = environment(user);
    environment.setLegacyItemUseForcedReleaseApplied(true);

    List<MovementSearchBranch> branches = branches(user, true, environment);

    assertEquals(1, branches.size());
    assertFalse(branches.get(0).moveConfig().isHandActive());
  }

  @Test
  void minecraft18DoesNotInferUseFromAnotherHotbarSlot() {
    User user = minecraft18UserWithSwordInSlot(1);

    List<MovementSearchBranch> branches = branches(user, false);

    assertEquals(1, branches.size());
    assertFalse(branches.get(0).moveConfig().isHandActive());
  }

  @Test
  void minecraft18ActiveHandProducesOnlyActiveMovement() {
    User user = minecraft18UserWithSwordInSlot(0);
    user.meta().inventory().activateHand();

    List<MovementSearchBranch> branches = branches(user, false);

    assertEquals(1, branches.size());
    assertTrue(branches.get(0).moveConfig().isHandActive());
  }

  @Test
  void minecraft18EstablishedUsePermitsOneDelayedSlotSwitchTransition() {
    User user = minecraft18UserWithSwordInSlot(0);
    user.meta().inventory().activateHand();
    user.meta().inventory().tickComplete();
    SimulationEnvironment environment = environment(user);

    List<MovementSearchBranch> branches = branches(user, false, environment, 1, 0);

    assertEquals(2, branches.size());
    assertTrue(branches.get(0).moveConfig().isHandActive());
    assertFalse(branches.get(1).moveConfig().isHandActive());
    assertTrue(
      branches.get(1).modifiedImmutableView(environment)
        .legacyItemUseSlotSwitchPending()
    );

    branches.get(1).modifiedMutableView(environment).commitTo(environment);
    assertTrue(environment.legacyItemUseSlotSwitchPending());
  }

  @Test
  void minecraft18DoesNotPermitAnotherUnconfirmedSlotSwitchTransition() {
    User user = minecraft18UserWithSwordInSlot(0);
    user.meta().inventory().activateHand();
    user.meta().inventory().tickComplete();
    SimulationEnvironment environment = environment(user);
    environment.setLegacyItemUseSlotSwitchPending(true);

    List<MovementSearchBranch> branches = branches(user, false, environment);

    assertEquals(1, branches.size());
    assertTrue(branches.get(0).moveConfig().isHandActive());
    assertFalse(
      branches.get(0).modifiedImmutableView(environment)
        .legacyItemUseSlotSwitchPending()
    );
  }

  @Test
  void minecraft18DoesNotConsumeSlotSwitchWithoutObservableInput() {
    User user = minecraft18UserWithSwordInSlot(0);
    user.meta().inventory().activateHand();
    user.meta().inventory().tickComplete();
    SimulationEnvironment environment = environment(user);

    List<MovementSearchBranch> branches = branches(user, false, environment);

    assertEquals(1, branches.size());
    assertTrue(branches.get(0).moveConfig().isHandActive());
    assertFalse(
      branches.get(0).modifiedImmutableView(environment)
        .legacyItemUseSlotSwitchPending()
    );
  }

  @Test
  void minecraft18HeldItemPacketConfirmsPendingSlotSwitchTransition() {
    User user = minecraft18UserWithSwordInSlot(0);
    user.meta().movement().setLegacyItemUseSlotSwitchPending(true);

    user.meta().inventory().receiveHeldItemSlot(1, null);

    assertFalse(user.meta().movement().legacyItemUseSlotSwitchPending());
  }

  @Test
  void minecraft18RepeatedHeldItemPacketDoesNotConfirmSlotSwitchTransition() {
    User user = minecraft18UserWithSwordInSlot(0);
    user.meta().movement().setLegacyItemUseSlotSwitchPending(true);

    user.meta().inventory().receiveHeldItemSlot(
      0, user.player().getInventory().getItem(0)
    );

    assertTrue(user.meta().movement().legacyItemUseSlotSwitchPending());
  }

  @Test
  void minecraft18ActiveUseAppliesPointTwoInputMultiplier() {
    User user = minecraft18UserWithSwordInSlot(0);
    user.meta().inventory().activateHand();
    SimulationEnvironment environment = environment(user);

    List<MovementSearchBranch> branches = branches(user, false, environment);

    SimulationEnvironment activeEnvironment = branches.get(0).modifiedImmutableView(environment);
    assertEquals(0.2F, activeEnvironment.itemUseSpeedMultiplier());
  }

  @Test
  void minecraft18ActiveUseStopsSprintingWithoutDroppingSlowdown() {
    User user = minecraft18UserWithSwordInSlot(0);
    user.meta().inventory().activateHand();
    SimulationEnvironment environment = environment(user);

    List<MovementSearchBranch> branches = branches(user, true, environment);

    assertEquals(1, branches.size());
    assertTrue(branches.get(0).moveConfig().isHandActive());
    assertFalse(branches.get(0).moveConfig().isSprinting());
    assertEquals(
      0.2F,
      branches.get(0).modifiedImmutableView(environment).itemUseSpeedMultiplier()
    );
  }

  @Test
  void modernInactivePacketStateProducesOnlyInactiveMovement() {
    User user = modernUserWithBow();

    List<MovementSearchBranch> branches = branches(user, false);

    assertEquals(1, branches.size());
    assertFalse(branches.get(0).moveConfig().isHandActive());
  }

  @Test
  void modernConfirmedSlotSwitchCanRestartUseBeforeItsPacketBoundary() {
    User user = user(ProtocolMetadata.VER_26_3);
    user.player().getInventory().setItem(0, new ItemStack(Material.BOW));
    user.player().getInventory().setItem(1, new ItemStack(Material.GOLDEN_APPLE));
    InventoryMetadata inventory = user.meta().inventory();
    inventory.setHeldItemSlot(0);
    user.meta().movement().setLegacyItemUseSlotSwitchPending(true);
    inventory.receiveHeldItemSlot(1, user.player().getInventory().getItem(1));
    MockSimulationEnvironment environment = environment(user);
    environment.setSprinting(true);

    List<MovementSearchBranch> branches = branches(
      user, true, environment, 1, 0
    );

    assertEquals(2, branches.size());
    assertFalse(branches.get(0).moveConfig().isHandActive());
    assertTrue(branches.get(1).moveConfig().isHandActive());
    assertTrue(branches.get(1).moveConfig().isSprinting());
    assertEquals(
      0.2F,
      branches.get(1).modifiedImmutableView(environment)
        .itemUseSpeedMultiplier()
    );

    inventory.tickComplete();
    List<MovementSearchBranch> nextTickBranches = branches(
      user, true, environment, 1, 0
    );
    assertEquals(1, nextTickBranches.size());
    assertFalse(nextTickBranches.get(0).moveConfig().isHandActive());
  }

  @Test
  void modernOrdinarySlotPacketDoesNotInferUnannouncedUse() {
    User user = user(ProtocolMetadata.VER_26_3);
    user.player().getInventory().setItem(0, new ItemStack(Material.BOW));
    user.player().getInventory().setItem(1, new ItemStack(Material.GOLDEN_APPLE));
    InventoryMetadata inventory = user.meta().inventory();
    inventory.setHeldItemSlot(0);
    inventory.receiveHeldItemSlot(1, user.player().getInventory().getItem(1));

    List<MovementSearchBranch> branches = branches(
      user, true, environment(user), 1, 0
    );

    assertEquals(1, branches.size());
    assertFalse(branches.get(0).moveConfig().isHandActive());
  }

  @Test
  void modernHeldSlotPacketKeepsInterruptedUseForOneMovement() {
    User user = modernUserWithBow(ProtocolMetadata.VER_1_20_5);
    user.player().getInventory().setItem(2, new ItemStack(Material.STONE));
    InventoryMetadata inventory = user.meta().inventory();
    inventory.activateHandIfUsable(PlayerHand.MAIN_HAND);
    inventory.tickComplete();

    inventory.receiveHeldItemSlot(
      2, user.player().getInventory().getItem(2)
    );
    MockSimulationEnvironment environment = environment(user);
    List<MovementSearchBranch> switchBranches = branches(
      user, true, environment, 1, 0
    );

    assertEquals(2, switchBranches.size());
    assertFalse(switchBranches.get(0).moveConfig().isHandActive());
    assertTrue(switchBranches.get(1).moveConfig().isHandActive());
    assertEquals(
      0.2F,
      switchBranches.get(1).modifiedImmutableView(environment)
        .itemUseSpeedMultiplier()
    );

    inventory.tickComplete();
    List<MovementSearchBranch> nextTickBranches = branches(
      user, true, environment(user), 1, 0
    );
    assertEquals(1, nextTickBranches.size());
    assertFalse(nextTickBranches.get(0).moveConfig().isHandActive());
  }

  @Test
  void modernActivePacketStateProducesOnlyActiveMovement() {
    User user = modernUserWithBow();
    user.meta().inventory().activateHand();
    SimulationEnvironment environment = environment(user);

    List<MovementSearchBranch> branches = branches(user, false, environment);

    assertEquals(1, branches.size());
    assertTrue(branches.get(0).moveConfig().isHandActive());
    assertEquals(
      0.2F,
      branches.get(0).modifiedImmutableView(environment).itemUseSpeedMultiplier()
    );
  }

  @Test
  void viaVersionShieldReplacementAllowsUnslowedMovement() {
    MinecraftVersion serverVersion = MinecraftVersion.current();
    try {
      MinecraftVersion.setCurrent(MinecraftVersions.VER1_8_0);
      User user = user(ProtocolMetadata.VER_1_20_5);
      user.player().getInventory().setItem(
        0, new ItemStack(Material.DIAMOND_SWORD)
      );
      InventoryMetadata inventory = user.meta().inventory();
      inventory.setHeldItemSlot(0);

      inventory.noteUseItemMovementSync();
      List<MovementSearchBranch> uncertainBranches = branches(
        user, true, environment(user)
      );
      assertEquals(2, uncertainBranches.size());
      assertFalse(uncertainBranches.get(0).moveConfig().isHandActive());
      assertTrue(uncertainBranches.get(1).moveConfig().isHandActive());

      inventory.clearViaVersionItemUseStateUncertainty();
      List<MovementSearchBranch> releasedBranches = branches(
        user, true, environment(user)
      );
      assertEquals(1, releasedBranches.size());
      assertFalse(releasedBranches.get(0).moveConfig().isHandActive());

      inventory.activateHandIfUsable(PlayerHand.MAIN_HAND);

      List<MovementSearchBranch> activationBranches = branches(
        user, true, environment(user)
      );

      assertEquals(2, activationBranches.size());
      assertTrue(activationBranches.get(0).moveConfig().isHandActive());
      assertFalse(activationBranches.get(1).moveConfig().isHandActive());

      inventory.tickComplete();
      List<MovementSearchBranch> establishedBranches = branches(
        user, true, environment(user)
      );
      assertEquals(2, establishedBranches.size());
      assertTrue(establishedBranches.get(0).moveConfig().isHandActive());
      assertFalse(establishedBranches.get(1).moveConfig().isHandActive());
    } finally {
      MinecraftVersion.setCurrent(serverVersion);
    }
  }

  @Test
  void modernEstablishedMainHandUsePermitsOneDelayedSlotSwitchTransition() {
    User user = modernUserWithBow(ProtocolMetadata.VER_26_3);
    user.meta().inventory().activateHand();
    user.meta().inventory().tickComplete();
    SimulationEnvironment environment = environment(user);

    List<MovementSearchBranch> branches = branches(user, false, environment, 1, 0);

    assertEquals(2, branches.size());
    assertTrue(branches.get(0).moveConfig().isHandActive());
    assertFalse(branches.get(1).moveConfig().isHandActive());
    assertTrue(
      branches.get(1).modifiedImmutableView(environment)
        .legacyItemUseSlotSwitchPending()
    );
  }

  @Test
  void modernHeldItemBurstCannotCreateANoSlowMovementBranch() {
    User user = user(ProtocolMetadata.VER_26_3);
    user.player().getInventory().setItem(0, new ItemStack(Material.BOW));
    user.player().getInventory().setItem(1, new ItemStack(Material.GOLDEN_APPLE));
    user.player().getInventory().setItem(2, new ItemStack(Material.SHIELD));
    user.player().getInventory().setItem(3, new ItemStack(Material.ARROW));
    InventoryMetadata inventory = user.meta().inventory();
    inventory.setHeldItemSlot(0);
    inventory.activateHandIfUsable(PlayerHand.MAIN_HAND);
    inventory.tickComplete();

    inventory.receiveHeldItemSlot(1, user.player().getInventory().getItem(1));
    inventory.receiveHeldItemSlot(2, user.player().getInventory().getItem(2));
    inventory.receiveHeldItemSlot(0, user.player().getInventory().getItem(0));
    inventory.clearItemUseTickTransitions();

    MockSimulationEnvironment environment = environment(user);
    environment.setLegacyItemUseSlotSwitchPending(
      user.meta().movement().legacyItemUseSlotSwitchPending()
    );
    List<MovementSearchBranch> branches = branches(
      user, false, environment, 1, 0
    );

    assertEquals(1, branches.size());
    assertTrue(branches.get(0).moveConfig().isHandActive());
    assertFalse(
      branches.get(0).modifiedImmutableView(environment)
        .legacyItemUseSlotSwitchPending()
    );
  }

  @Test
  void modernOffhandUseDoesNotPermitMainHandSlotSwitchTransition() {
    User user = modernUserWithBow(ProtocolMetadata.VER_26_3);
    user.player().getInventory().setItemInOffHand(new ItemStack(Material.BOW));
    user.meta().inventory().activateHandIfUsable(PlayerHand.OFF_HAND);
    user.meta().inventory().tickComplete();

    List<MovementSearchBranch> branches = branches(
      user, false, environment(user), 1, 0
    );

    assertEquals(1, branches.size());
    assertTrue(branches.get(0).moveConfig().isHandActive());
  }

  @Test
  void minecraft19ActiveUseStopsExistingSprintBeforeMovement() {
    User user = modernUserWithBow(ProtocolMetadata.VER_1_9);
    user.meta().inventory().activateHand();
    MockSimulationEnvironment environment = environment(user);
    environment.setSprinting(true);

    List<MovementSearchBranch> branches = branches(user, true, environment);

    assertEquals(1, branches.size());
    assertTrue(branches.get(0).moveConfig().isHandActive());
    assertFalse(branches.get(0).moveConfig().isSprinting());
  }

  @Test
  void minecraft114ActiveUsePreservesExistingSprint() {
    User user = modernUserWithBow(ProtocolMetadata.VER_1_14);
    user.meta().inventory().activateHand();
    MockSimulationEnvironment environment = environment(user);
    environment.setSprinting(true);

    List<MovementSearchBranch> branches = branches(user, true, environment);

    assertEquals(1, branches.size());
    assertTrue(branches.get(0).moveConfig().isHandActive());
    assertTrue(branches.get(0).moveConfig().isSprinting());
  }

  @Test
  void minecraft1214StopsItemUseSprintAboveWater() {
    User user = modernUserWithBow(ProtocolMetadata.VER_1_21_4);
    user.meta().inventory().activateHand();
    MockSimulationEnvironment environment = environment(user);
    environment.setSprinting(true);

    List<MovementSearchBranch> branches = branches(user, true, environment);

    assertEquals(1, branches.size());
    assertFalse(branches.get(0).moveConfig().isSprinting());
  }

  @Test
  void minecraft1214PreservesItemUseSprintUnderwater() {
    User user = modernUserWithBow(ProtocolMetadata.VER_1_21_4);
    user.meta().inventory().activateHand();
    MockSimulationEnvironment environment = environment(user);
    environment.setSprinting(true);
    environment.setEyesInWater(true);

    List<MovementSearchBranch> branches = branches(user, true, environment);

    assertEquals(1, branches.size());
    assertTrue(branches.get(0).moveConfig().isSprinting());
  }

  @Test
  void minecraft1215PreservesItemUseSprintAgain() {
    User user = modernUserWithBow(ProtocolMetadata.VER_1_21_5);
    user.meta().inventory().activateHand();
    MockSimulationEnvironment environment = environment(user);
    environment.setSprinting(true);

    List<MovementSearchBranch> branches = branches(user, true, environment);

    assertEquals(1, branches.size());
    assertTrue(branches.get(0).moveConfig().isSprinting());
  }

  private static User minecraft18UserWithSwordInSlot(int slot) {
    User user = user(ProtocolMetadata.VER_1_8);
    user.player().getInventory().setItem(slot, new ItemStack(Material.DIAMOND_SWORD));
    user.meta().inventory().setHeldItemSlot(0);
    return user;
  }

  private static User modernUserWithBow() {
    return modernUserWithBow(ProtocolMetadata.VER_1_21_11);
  }

  private static User modernUserWithBow(int protocolVersion) {
    User user = user(protocolVersion);
    user.player().getInventory().setItem(0, new ItemStack(Material.BOW));
    user.player().getInventory().setItem(1, new ItemStack(Material.ARROW));
    user.meta().inventory().setHeldItemSlot(0);
    return user;
  }

  private static List<MovementSearchBranch> branches(User user, boolean sprinting) {
    return branches(user, sprinting, environment(user));
  }

  private static List<MovementSearchBranch> branches(
    User user,
    boolean sprinting,
    SimulationEnvironment environment
  ) {
    return branches(user, sprinting, environment, 0, 0);
  }

  private static List<MovementSearchBranch> branches(
    User user,
    boolean sprinting,
    SimulationEnvironment environment,
    int forward,
    int strafe
  ) {
    return branches(user, sprinting, environment, forward, strafe, true);
  }

  private static List<MovementSearchBranch> branches(
    User user,
    boolean sprinting,
    SimulationEnvironment environment,
    int forward,
    int strafe,
    boolean enforceItemSlowdown
  ) {
    MovementSearchInput input = MovementSearchInput.forTick(user, null, environment, enforceItemSlowdown);
    MovementSearchBranch inputBranch = MovementSearchBranch.blank(input)
      .withKeypress(forward, strafe)
      .withSprintingSetTo(sprinting);
    List<MovementSearchBranch> output = new ArrayList<>();
    new UseItemBrancher().branch(input, inputBranch, output);
    return output;
  }

  private static MockSimulationEnvironment environment(User user) {
    MockSimulationEnvironment environment = new MockSimulationEnvironment(user);
    environment.setPose(Pose.STANDING);
    return environment;
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
}
