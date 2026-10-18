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


import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import java.util.Collection;

final class UseItemBrancher extends MovementSearchBrancher {
  @Override
  public void branch(
    MovementSearchInput input,
    MovementSearchBranch inputBranch,
    Collection<MovementSearchBranch> outputBranches
  ) {
    InventoryMetadata inventory = input.user().meta().inventory();
    ProtocolMetadata protocol = input.user().meta().protocol();
    if (!input.detectNoSlowdown()
      && (inventory.handActive() || input.environment().legacyItemUseReleaseUncertain())) {
      // Disabling enforcement permits unslowed movement without inventing a
      // slot switch or discarding the tracked client's slowed candidate.
      outputBranches.add(inputBranch.withHandActive(false));
    }
    if (!protocol.combatUpdate()) {
      branchLegacy(input, inputBranch, outputBranches, inventory);
      return;
    }

    boolean viaVersionShieldReplacement = protocol.viaVersionShieldBlockReplacement()
      && ItemProperties.isSwordItem(inventory.heldItem());
    if (!inventory.handActive()) {
      outputBranches.add(inputBranch.withHandActive(false));
      if (inventory.viaVersionItemUseStateUncertain()
        && itemUseStateObservable(inputBranch)) {
        ItemProperties.ItemUseProfile profile = ItemProperties.itemUseProfile(
          input.user(), inventory.heldItem()
        );
        if (profile != null) {
          // The 1.17-1.21.5 client sends a position/rotation sync before
          // use-item. ViaVersion can translate away the hand packet and its
          // corresponding release while the client remains slowed, so retain
          // both states after that sync until an explicit release is observed.
          outputBranches.add(modernActiveBranch(
            input, inputBranch, protocol,
            profile.canSprint(), profile.speedMultiplier()
          ));
        }
      }
      if (inventory.itemUseInterruptedBySlotSwitchThisTick()
        && itemUseStateObservable(inputBranch)) {
        // The selected-slot packet can race the movement that still used the
        // old stack. Preserve that final slowed movement for this packet only;
        // InventoryMetadata clears the interrupted snapshot at tick end.
        outputBranches.add(modernActiveBranch(
          input, inputBranch, protocol,
          inventory.interruptedItemAllowsSprinting(),
          inventory.interruptedItemUseSpeedMultiplier()
        ));
      }
      ItemProperties.ItemUseProfile selectedItemProfile = ItemProperties.itemUseProfile(
        input.user(), inventory.heldItem()
      );
      // A held-slot packet can confirm the local switch inferred on the
      // preceding move. If use remains held, handleKeybinds starts the newly
      // selected item before the next player tick; its use packet may be
      // observed at the following movement boundary by protocol translation.
      if (inventory.itemUseSlotSwitchConfirmedThisTick()
        && selectedItemProfile != null
        && itemUseStateObservable(inputBranch)) {
        outputBranches.add(modernActiveBranch(
          input, inputBranch, protocol,
          selectedItemProfile.canSprint(), selectedItemProfile.speedMultiplier()
        ));
      }
      publishDebugState(input, inventory, "S");
      return;
    }

    MovementSearchBranch activeBranch = modernActiveBranch(
      input, inputBranch, protocol,
      inventory.activeItemAllowsSprinting(), inventory.activeItemUseSpeedMultiplier()
    );
    if (input.environment().legacyItemUseSlotSwitchPending()) {
      activeBranch = activeBranch.withLegacyItemUseSlotSwitchResolved();
    }
    outputBranches.add(activeBranch);

    boolean activeViaVersionShieldReplacement = viaVersionShieldReplacement
      && inventory.activeHand() == PlayerHand.MAIN_HAND;
    if (activeViaVersionShieldReplacement) {
      // ViaVersion implements 1.8 sword blocking by showing a fake shield to
      // the modern client. That translated blocking state does not prove the
      // client locally entered item use, so its movement can remain unslowed.
      outputBranches.add(inputBranch.withHandActive(false));
    }

    // Minecraft#tick handles hotbar keys after MultiPlayerGameMode#tick sends
    // the selected slot, but before the player tick validates the used stack
    // and applies movement input. The client can therefore stop main-hand use
    // for one move before its selected-slot packet reaches the server.
    if (inventory.activeHand() == PlayerHand.MAIN_HAND
      && inventory.handActiveTicks > 0
      && !activeViaVersionShieldReplacement
      && itemUseStateObservable(inputBranch)
      && !input.environment().legacyItemUseSlotSwitchPending()) {
      outputBranches.add(inputBranch.withLegacyItemUseSlotSwitch());
    }
    publishDebugState(input, inventory, "R");
  }

  private MovementSearchBranch modernActiveBranch(
    MovementSearchInput input,
    MovementSearchBranch inputBranch,
    ProtocolMetadata protocol,
    boolean itemAllowsSprinting,
    float speedMultiplier
  ) {
    MovementSearchBranch activeBranch = inputBranch.withHandActive(true, speedMultiplier);
    if (inputBranch.isSprinting()
      && !protocol.viaVersionShieldBlockReplacement()
      && !protocol.sprintCompatibleWithHandActive(
        input.environment().isSprinting(),
        input.environment().areEyesInWater(),
        itemAllowsSprinting
      )) {
      activeBranch = activeBranch.withSprintingSetTo(false);
    }
    return activeBranch;
  }

  private void branchLegacy(
    MovementSearchInput input,
    MovementSearchBranch inputBranch,
    Collection<MovementSearchBranch> outputBranches,
    InventoryMetadata inventory
  ) {
    if (input.environment().legacyItemUseForcedReleaseApplied()) {
      outputBranches.add(inputBranch.withHandActive(false));
      publishDebugState(input, inventory, "F");
      return;
    }
    if (input.environment().legacyItemUseReleaseUncertain()) {
      // The injected legacy release reaches the client asynchronously. Keep
      // slowdown available until a movement packet selects the released state.
      outputBranches.add(
        inputBranch.withSprintingSetTo(false).withHandActive(true)
      );
      if (itemUseStateObservable(inputBranch)) {
        outputBranches.add(
          inputBranch.withLegacyItemUseReleaseResolved().withHandActive(false)
        );
      }
      publishDebugState(input, inventory, "U");
      return;
    }
    // EntityPlayerSP#onLivingUpdate scales input while using an item, then
    // clears sprinting before movement. Resolve that ordering on the branch;
    // never discard tracked item use to preserve a sprinting candidate.
    if (inventory.handActive()) {
      MovementSearchBranch activeBranch = inputBranch
        .withSprintingSetTo(false)
        .withHandActive(true);
      if (input.environment().legacyItemUseSlotSwitchPending()) {
        activeBranch = activeBranch.withLegacyItemUseSlotSwitchResolved();
      }
      outputBranches.add(activeBranch);

      // InventoryPlayer.currentItem changes before EntityPlayer#onUpdate clears
      // itemInUse, but C09 is sent at the start of the following client tick.
      // Permit that unannounced transition once and require the delayed slot
      // packet before permitting another one.
      if (inventory.handActiveTicks > 0
        && itemUseStateObservable(inputBranch)
        && !input.environment().legacyItemUseSlotSwitchPending()) {
        outputBranches.add(inputBranch.withLegacyItemUseSlotSwitch());
      }
      publishDebugState(input, inventory, "R");
      return;
    }
    outputBranches.add(inputBranch.withHandActive(false));
    publishDebugState(input, inventory, "S");
  }

  private boolean itemUseStateObservable(MovementSearchBranch branch) {
    return branch.moveConfig().forward() != 0
      || branch.moveConfig().strafe() != 0
      || branch.isSprinting();
  }

  private void publishDebugState(
    MovementSearchInput input,
    InventoryMetadata inventory,
    String state
  ) {
    if (input.user().receives(MessageChannel.DEBUG_ITEM_USE)
      && input.shouldUpdateItemUseDebug()) {
      inventory.itemUsePermissionDebug(state);
    }
  }
}
