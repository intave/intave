package de.jpx3.intave.user;

import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.tools.items.PlayerEnchantmentHelper;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class UserMetaInventoryData {
  private final Player player;
//  private ItemStack heldItem;
  private int handSlot;
  private boolean handActive;

  private boolean foodItem;
  private boolean inventoryOpen;
  public int handActiveTicks;
  public int pastItemUsageTransition;
  public int pastHotBarSlotChange;
  public int selectedHotBarSlot;

  public UserMetaInventoryData(Player player) {
    this.player = player;
    if(player != null) {
      this.handSlot = player.getInventory().getHeldItemSlot();
    }
//    this.heldItem = resolveMaterialInHand();
  }

//  public void resynchronizeHeldItem() {
//    this.heldItem = resolveMaterialInHand();
//  }

//  private ItemStack resolveMaterialInHand() {
//    return player == null ? null : player.getItemInHand();
//  }

  public boolean handActive() {
    return handActive;
  }

  public ItemStack heldItem() {
    return player == null ? null : player.getInventory().getItem(handSlot);//heldItem;
  }

  public Material heldItemType() {
    ItemStack heldItem = heldItem();
    return heldItem == null || heldItem.getAmount() == 0 ? Material.AIR : heldItem.getType();
  }

  public boolean inventoryOpen() {
    return inventoryOpen;
  }
//
//  public void setHeldItem(ItemStack heldItem) {
//    this.heldItem = heldItem;
//  }

  public void deactivateHand() {
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    ItemStack heldItem = heldItem();
    if (heldItem != null && PlayerEnchantmentHelper.tridentRiptideEnchanted(heldItem)) {
      movementData.pastRiptideSpin = 0;
    }
    this.handActive = false;
    this.pastItemUsageTransition = 0;
    this.handActiveTicks = 0;
  }

  public void activateHand() {
    this.handActive = true;
    this.foodItem = InventoryUseItemHelper.foodItemRegistry().foodConsumable(player.getFoodLevel(), heldItemType());
    this.pastItemUsageTransition = 0;
    this.handActiveTicks = 0;
  }

  public void setHeldItemSlot(int slot) {
    this.handSlot = slot;
  }

/*  public void applySlotSwitch() {
    int previousItemSlot = this.selectedHotBarSlot;
    int newItemSlot = this.selectedHotBarSlot + 1;
    if (newItemSlot > 8) {
      newItemSlot = 0;
    }
    setHeldItemSlot(newItemSlot);
    setHeldItemSlot(previousItemSlot);
  }

  private void setHeldItemSlot(int slot) {
    if(player == null) {
      return;
    }
    PlayerInventory inventory = player.getInventory();
    inventory.setHeldItemSlot(slot);
  }*/

  public void setInventoryOpen(boolean inventoryOpen) {
    this.inventoryOpen = inventoryOpen;
  }

  public boolean foodItem() {
    return foodItem;
  }
}