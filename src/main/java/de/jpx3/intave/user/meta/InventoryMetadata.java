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

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.player.Enchantments;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.RIPTIDE_SPIN;

public final class InventoryMetadata {
  private final User user;
  private final Player player;
  private final List<String> whitelistedItemIdRequests = new ArrayList<>();
  public int handActiveTicks, pastHandActiveTicks = 100;
  public int pastItemUsageTransition;
  public int pastHotBarSlotChange;
  public long lastWCCReset;
  public int windowClickCounter;
  public boolean forceInventoryOnClickOpen = true;
  public boolean blockNextArrow = false;
  public long lastBlockArrowRequest;
  public long lastFoodConsumptionBlockRequest;
  public boolean releaseItemNextTick = false;
  public boolean activatedItemThisTick = false;
  public boolean deactivatedItemThisTick = false;
  public Material releaseItemType = Material.AIR;
  public volatile SlotSwitchData slotSwitchData;
  public int pastSlotSwitch;
  private boolean inventoryOpen;
  private int handSlot;
  private boolean handSlotChangedSinceActivation;
  private volatile boolean handActive;
  private final Lock handActiveLock = new ReentrantLock();
  private Material activeItemType;
  private List<String> items = new ArrayList<>();
  private boolean foodItem;
  public int lastBlockSequenceNumber;

  public InventoryMetadata(Player player, User user) {
	  this.user = user;
	  this.player = player;
    if (player != null) {
      this.handSlot = player.getInventory().getHeldItemSlot();
    }
    activeItemType = Material.AIR;
  }

  public boolean handActive() {
    return handActive;
  }

  public void registerSkullRequest(String name) {
    if (!whitelistedItemIdRequests.contains(name)) {
      whitelistedItemIdRequests.add(name);
    }
  }

  public boolean skullWhitelisted(String id) {
    return whitelistedItemIdRequests.contains(id);
  }

  @Nullable
  public ItemStack heldItem() {
    return player == null ? null : player.getInventory().getItem(handSlot); // heldItem;
  }

  @Nullable
  public ItemStack offhandItem() {
    if (!MinecraftVersions.VER1_9_0.atOrAbove()) {
      return null;
    }
    return player == null ? null : player.getInventory().getItemInOffHand();
  }

  public boolean usableItemInEitherHand() {
    return ItemProperties.canItemBeUsed(user, heldItem()) ||
      ItemProperties.canItemBeUsed(user, offhandItem());
  }

  public boolean usableItemInEitherHandOrHotbar() {
    if (player == null) {
      return false;
    }
    if (usableItemInEitherHand()) {
      return true;
    }
    for (int i = 0; i < 9; i++) {
      ItemStack item = player.getInventory().getItem(i);
      if (ItemProperties.canItemBeUsed(user, item)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public Material offhandItemType() {
    ItemStack item = offhandItem();
    return item == null || item.getAmount() == 0 ? Material.AIR : item.getType();
  }

  public boolean offhandItemPrimary() {
    return ItemProperties.canItemBeUsed(user, offhandItem()) && !ItemProperties.canItemBeUsed(user, heldItem());
  }

  public int handSlot() {
    return handSlot;
  }

  public Material heldItemType() {
    ItemStack heldItem = heldItem();
    return heldItem == null || heldItem.getAmount() == 0 ? Material.AIR : heldItem.getType();
  }

  public boolean inventoryOpen() {
    return inventoryOpen;
  }

  public void activateHand() {
    handActiveLock.lock();
    try {
      if (handActive && !handSlotChangedSinceActivation) {
        return;
      }
      if (handActive) {
        releaseItemNextTick = false;
        releaseItemType = Material.AIR;
      }
      this.handActive = true;
      this.handSlotChangedSinceActivation = false;

      User user = UserRepository.userOf(player);
      user.meta().movement().handItemSimulationFails = 0;

      if (offhandItemPrimary()) {
        this.foodItem = ItemProperties.foodConsumable(user, offhandItemType());
        this.activeItemType = offhandItemType();
      } else {
        this.foodItem = ItemProperties.foodConsumable(user, heldItemType());
        this.activeItemType = heldItemType();
      }
      this.pastItemUsageTransition = 0;
      this.handActiveTicks = 0;
      this.activatedItemThisTick = true;

      if (IntaveControl.DEBUG_ITEM_USAGE) {
        Material activeItem = this.activeItemType;
        user.sendMessage("Item usage started: " + activeItem);
      }
    } finally {
      handActiveLock.unlock();
    }
  }

  public void deactivateHand() {
    handActiveLock.lock();
    try {
      User user = UserRepository.userOf(player);
      MovementMetadata movementData = user.meta().movement();
      if (!handActive) {
        return;
      }
      ItemStack heldItem = heldItem();
      ItemStack offhandItem = offhandItem();
      if ((heldItem != null && Enchantments.tridentRiptideEnchanted(heldItem))
        || (offhandItem != null && Enchantments.tridentRiptideEnchanted(offhandItem))) {
        movementData.activeTick(RIPTIDE_SPIN);
        movementData.highestLocalRiptideLevel = Math.max(
          movementData.highestLocalRiptideLevel,
          Math.max(Enchantments.resolveRiptideModifier(heldItem), Enchantments.resolveRiptideModifier(offhandItem))
        );
        movementData.onGroundWithRiptide = movementData.onGround;
      }
      this.handActive = false;
      this.handSlotChangedSinceActivation = false;
      this.pastItemUsageTransition = 0;
      this.handActiveTicks = 0;
      this.deactivatedItemThisTick = true;
      Material activeItem = this.activeItemType;
      if (IntaveControl.DEBUG_ITEM_USAGE) {
        user.sendMessage("Item usage ended: " + activeItem);
//        Thread.dumpStack();
        System.out.println("Item usage ended: " + activeItem);
      }
      this.activeItemType = Material.AIR;
    } finally {
      handActiveLock.unlock();
    }
  }

  public Material activeItemType() {
    return activeItemType;
  }

  public void releaseItemNextTick() {
    if (IntaveControl.DEBUG_ITEM_USAGE) {
      user.sendMessage("Forceful item release next tick");
    }
    releaseItemNextTick = true;
    releaseItemType = handActive ? activeItemType : heldItemType();
  }

  public void updateSlotSwitch() {
    if (slotSwitchData != null) {
      int slot = slotSwitchData.slot();
      ItemStack item = slotSwitchData.item();

      boolean primaryItemUsable = ItemProperties.canItemBeUsed(user, item);
      boolean offhandItemUsage = ItemProperties.canItemBeUsed(user, offhandItem());
      boolean handActive = (primaryItemUsable || offhandItemUsage) && handActive();
      if (!handActive) {
       deactivateHand();
      }
      setHeldItemSlot(slot);
      pastHotBarSlotChange = 0;
      slotSwitchData = null;
    }
  }

  public void setHeldItemSlot(int slot) {
    if (this.handSlot != slot) {
      this.handSlotChangedSinceActivation = true;
    }
    this.handSlot = slot;
  }

  @Deprecated
  public void setHandActive(boolean handActive) {
    this.handActive = handActive;
  }

  public void restoreRecordedState(
    int heldSlot,
    boolean handActive,
    int handActiveTicks,
    int pastHandActiveTicks,
    int pastItemUsageTransition,
    boolean inventoryOpen,
    Material activeItemType,
    boolean foodItem,
    boolean releaseItemNextTick,
    Material releaseItemType,
    boolean activatedItemThisTick,
    boolean deactivatedItemThisTick
  ) {
    this.handSlot = heldSlot;
    this.handSlotChangedSinceActivation = false;
    this.handActive = handActive;
    this.handActiveTicks = handActiveTicks;
    this.pastHandActiveTicks = pastHandActiveTicks;
    this.pastItemUsageTransition = pastItemUsageTransition;
    this.inventoryOpen = inventoryOpen;
    this.activeItemType = activeItemType;
    this.foodItem = foodItem;
    this.releaseItemNextTick = releaseItemNextTick;
    this.releaseItemType = releaseItemType;
    this.activatedItemThisTick = activatedItemThisTick;
    this.deactivatedItemThisTick = deactivatedItemThisTick;
  }

  public void updateInventoryOpenState(boolean inventoryOpen) {
    User user = UserRepository.userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    if (!inventoryOpen && clientData.supportsInventoryAchievementPacket()) {
      this.forceInventoryOnClickOpen = true;
    }
    if (inventoryOpen != this.inventoryOpen) {
      releaseItemNextTick();
      if (user.receives(MessageChannel.DEBUG_ITEM_RESETS)) {
        user.sendMessage(IntavePlugin.prefix() + "Requesting item usage reset as " + ChatColor.RED + " inventory was toggled ");
      }
    }
//    deactivateHand();
    this.inventoryOpen = inventoryOpen;
  }

  public boolean foodItem() {
    return foodItem;
  }

  public void tickComplete() {
    pastSlotSwitch++;
    pastHotBarSlotChange++;
    pastItemUsageTransition++;

    if (handActive()) {
      handActiveTicks++;
      pastHandActiveTicks = 0;
    } else {
      pastHandActiveTicks++;
      handActiveTicks = 0;
    }
  }

  private static final Material CROSSBOW = MaterialSearch.materialThatIsNamed("CROSSBOW");

  public boolean couldChargeCrossbow() {
//    User user = UserRepository.userOf(player);
    if (CROSSBOW == null) {
      return false;
    }
    return (heldItemType() == CROSSBOW || offhandItemType() == CROSSBOW) && hasArrowInInventory();
  }

  private boolean hasArrowInInventory() {
    for (ItemStack item : player.getInventory().getContents()) {
      if (item == null) {
        continue;
      }
      if (item.getType() == Material.ARROW) {
        return true;
      }
    }
    return false;
  }

  public List<String> items() {
    return items;
  }

  public void setItems(List<String> items) {
    this.items = items;
  }

  public static class SlotSwitchData {
    private final int slot;
    private final ItemStack stack;

    public SlotSwitchData(int slot, ItemStack stack) {
      this.slot = slot;
      this.stack = stack;
    }

    public int slot() {
      return slot;
    }

    public ItemStack item() {
      return stack;
    }
  }
}
