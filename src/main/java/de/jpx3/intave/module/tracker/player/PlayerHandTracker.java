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


import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packetperfect.OnReceive;
import de.jpx3.intave.module.linker.packetperfect.OnSend;
import de.jpx3.intave.packet.contract.immutable.*;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.share.PlayerDigAction;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.PunishmentMetadata;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

import static de.jpx3.intave.packet.ListenerPriority.*;

public class PlayerHandTracker extends Module {
//  @BukkitEventSubscription
//  public void itemConsume(FoodLevelChangeEvent event) {
//    if (!(event.getEntity() instanceof Player)) {
//      return;
//    }
//    Player player = (Player) event.getEntity();
//    User user = UserRepository.userOf(player);
//    InventoryMetadata inventoryData = user.meta().inventory();
//    if (event.getFoodLevel() >= 20 && inventoryData.foodItem() && inventoryData.handActive()) {
//      inventoryData.deactivateHand();
//    }
//  }

  @BukkitEventSubscription(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void entityFoodChange(FoodLevelChangeEvent event) {
    HumanEntity entity = event.getEntity();
    if (!(entity instanceof Player)) {
      return;
    }

    Player player = (Player) entity;
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();
    int foodLevel = event.getFoodLevel();

    if (foodLevel >= 20 && inventoryData.handActive() && inventoryData.foodItem()) {
      ItemStack activeItem = inventoryData.activeHand() == PlayerHand.OFF_HAND
        ? inventoryData.offhandItem() : inventoryData.heldItem();
      if (!ItemProperties.foodConsumable(user, activeItem)) {
        if (user.meta().protocol().combatUpdate()) {
          inventoryData.markServerItemUseCompleted();
        } else {
          inventoryData.deactivateHand();
        }
      }
    }
  }

  @BukkitEventSubscription(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void receiveItemConsume(PlayerItemConsumeEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();
    if (user.meta().protocol().combatUpdate()) {
      inventoryData.markServerItemUseCompleted();
    } else {
      inventoryData.deactivateHand();
    }
  }

  @OnReceive(priority = FIRST)
  public void receiveSlotSwitch(User user, Cancellable cancellable, HeldItemSlotPacket packet) {
    if (cancellable.isCancelled()) {
      return;
    }
    InventoryMetadata inventoryData = user.meta().inventory();

    int slot = packet.slot();

    if (isInvalidSlot(slot)) {
      return;
    }

    if (slot != inventoryData.handSlot() && user.meta().protocol().sendsClientTickEnd()) {
      inventoryData.recordPreviousHeldItemForReach(inventoryData.heldItem());
    }

    ItemStack item = user.player().getInventory().getItem(slot);
    if (slot != inventoryData.handSlot()) {
      user.meta().attack().attackCooldown().observeHeldItem(item);
    }
    inventoryData.receiveHeldItemSlot(slot, item);
    if (inventoryData.handActive() && !inventoryData.offhandItemPrimary()) {
      if (!ItemProperties.canItemBeUsed(user, inventoryData.heldItem())) {
        inventoryData.blockNextArrow = true;
        inventoryData.lastBlockArrowRequest = System.currentTimeMillis();
        if (user.receives(MessageChannel.DEBUG_ITEM_USE)) {
          user.sendMessage(IntavePlugin.prefix() + " Detected item switch on active item, released hand and blocking impending arrow shot");
        }
      }
    }
  }

  @OnReceive
  public void receiveClientTickEnd(User user, Cancellable cancellable, ClientTickEndPacket packet) {
    if (!cancellable.isCancelled()) {
      user.meta().inventory().clearPreviousSpearThisTick();
    }
  }

  @OnSend
  public void sentSlotSwitch(User user, HeldItemSlotPacket packet, PacketSendEvent event) {
    if (event.isCancelled()) {
      return;
    }
    int slot = packet.slot();

    if (isInvalidSlot(slot)) {
      return;
    }

    Modules.feedback().synchronize(user, event, () -> {
      if (slot != user.meta().inventory().handSlot()) {
        user.meta().attack().attackCooldown().reset();
      }
      user.meta().inventory().setHeldItemSlot(slot);
      user.meta().inventory().clearPreviousSpearThisTick();
    });
  }

  private boolean isInvalidSlot(int slot) {
    return slot >= 36 || slot < 0;
  }

  @OnReceive(priority = FIRST)
  public void receiveBlockPlace(
    User user, Cancellable cancellable, BlockInteractionPacket packet
  ) {
    if (!cancellable.isCancelled() && packet.isItemUseRequest()) {
      receiveItemUseRequest(
        cancellable, user, packet.hand(), !packet.isDirectItemUseRequest()
      );
    }
  }

  @OnSend(priority = LAST)
  public void sentInventoryItems(
    PacketSendEvent event, User user, ItemUpdates packet
  ) {
    if (event.isCancelled()) {
      return;
    }
    InventoryMetadata inventory = user.meta().inventory();
    if (!inventory.handActive()) {
      return;
    }
    int activeSlot = inventory.activeClientInventorySlot(packet.containerId());
    Map<Integer, de.jpx3.intave.item.ItemStack> items = packet.itemMap();
    if (activeSlot < 0 || !items.containsKey(activeSlot)) {
      return;
    }
    de.jpx3.intave.item.ItemStack replacement = items.get(activeSlot);
    Material replacementType = replacement == null ? Material.AIR : replacement.bukkitMaterial().orElse(Material.AIR);
    long useSequence = inventory.activeItemUseSequence();
    Modules.feedback().synchronize(
      user, event,
      () -> {
        if (inventory.applyClientActiveItemReplacement(useSequence, replacementType)) {
          Modules.physicsTestRecorder().recordActiveItemReplacement(
            user, replacementType
          );
        }
      }
    );
  }

  void receiveItemUseRequest(
    Cancellable cancellable, User user, PlayerHand hand
  ) {
    receiveItemUseRequest(cancellable, user, hand, false);
  }

  void receiveItemUseRequest(
    Cancellable cancellable,
    User user,
    PlayerHand hand,
    boolean useOnBlock
  ) {
    InventoryMetadata inventoryData = user.meta().inventory();
    PunishmentMetadata punishmentData = user.meta().punishment();

    if (hand == null && user.meta().protocol().combatUpdate()) {
      // Modern vanilla use packets always contain one of the two hand enum
      // values. Do not turn a malformed/invalid hand into a guessed valid use.
      return;
    }

    ItemStack heldItem = inventoryData.heldItem();
    boolean sword = heldItem != null && heldItem.getType().name().endsWith("_SWORD");

    if (sword && System.currentTimeMillis() - punishmentData.timeLastBlockCancel < 5000) {
      cancellable.setCancelled(true);
      return;
    }

    inventoryData.activateHandIfUsable(hand, useOnBlock);
  }

  @OnReceive(priority = LATER)
  public void receiveBlockDigging(User user, Cancellable cancellable, BlockDigPacket packet) {
    if (cancellable.isCancelled()) {
      return;
    }
    InventoryMetadata inventoryData = user.meta().inventory();

    PlayerDigAction digType = packet.action();
    if (digType == null) return;

    if (!packet.hasBlockPosition()) return;

    if (digType == PlayerDigAction.RELEASE_USE_ITEM) {
      // Every supported vanilla client releases item use with BlockPos.ZERO
      // and DOWN. Spoofed positions/faces must not clear slowdown state.
      if (!packet.isCanonicalItemUseRelease()) {
        inventoryData.markSuspiciousNoSlowdownPacketPatternThisTick();
        return;
      }
      if (!inventoryData.handActive()) {
        return;
      }
    }

    if (IntaveControl.DEBUG_ITEM_USAGE) {
      user.sendMessage("Digtype: " + digType);
    }

    switch (digType) {
      case RELEASE_USE_ITEM:
        inventoryData.clearViaVersionItemUseStateUncertainty();
      case DROP_ALL_ITEMS:
      case DROP_ITEM: {
        inventoryData.deactivateHand();
        break;
      }
    }
  }
}
