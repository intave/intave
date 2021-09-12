package de.jpx3.intave.user.meta;

import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.annotate.refactoring.IdoNotBelongHere;
import de.jpx3.intave.player.Enchantments;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Relocate
public final class InventoryMetadata {
  private final Player player;
  private int handSlot;
  private boolean handActive;

  private Material activeItem;
  private boolean foodItem;
  private boolean inventoryOpen;
  public int handActiveTicks, pastHandActiveTicks = 100;
  public int pastItemUsageTransition;
  public int pastHotBarSlotChange;
  public int awaitingSlotSet = -1;
  public boolean forceInventoryOnClickOpen = true;
  public volatile int pastSlotSwitch = 100;
  public boolean blockNextArrow = false;

  public InventoryMetadata(Player player) {
    this.player = player;
    if (player != null) {
      this.handSlot = player.getInventory().getHeldItemSlot();
    }
    activeItem = Material.AIR;
  }

  private ItemStack resolveMaterialInHand() {
    return player == null ? null : player.getItemInHand();
  }

  public boolean handActive() {
    return handActive;
  }

  public ItemStack heldItem() {
    return player == null ? null : player.getInventory().getItem(handSlot);//heldItem;
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

  @IdoNotBelongHere
  public void deactivateHand() {
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    ItemStack heldItem = heldItem();
    if (heldItem != null && Enchantments.tridentRiptideEnchanted(heldItem)) {
      movementData.pastRiptideSpin = 0;
    }
    this.handActive = false;
    this.pastItemUsageTransition = 0;
    this.handActiveTicks = 0;
    this.activeItem = Material.AIR;
  }

  public void activateHand() {
    this.handActive = true;
    this.foodItem = ItemProperties.foodConsumable(player, heldItemType());
    this.pastItemUsageTransition = 0;
    this.handActiveTicks = 0;
    this.activeItem = heldItemType();
  }

  public Material activeItem() {
    return activeItem;
  }

//  @IdoNotBelongHere
//  @Deprecated
//  public void applySlotSwitch() {
//    if (!necessarySlotSwitch(this.handSlot)) {
//      return;
//    }
//    int previousItemSlot = this.handSlot;
//    int newItemSlot = this.handSlot + 1;
//    if (newItemSlot > 8) {
//      newItemSlot = 7;
//    }
//    int finalNewItemSlot = newItemSlot;
//    pastSlotSwitch = 0;
//    Synchronizer.synchronize(() -> {
//      pastSlotSwitch = 0;
//      awaitingSlotSet = previousItemSlot;
//      player.getInventory().setHeldItemSlot(finalNewItemSlot);
//      player.updateInventory();
//    });
//  }

//  @IdoNotBelongHere
//  private boolean necessarySlotSwitch(int slot) {
//    PlayerInventory inventory = player.getInventory();
//    ItemStack item = inventory.getItem(slot);
//    if (item == null) {
//      return false;
//    }
//    return ItemProperties.canItemBeUsed(player, item);
//  }

  public void setHeldItemSlot(int slot) {
    this.handSlot = slot;
  }

  public void setHandActive(boolean handActive) {
    this.handActive = handActive;
  }

  public void updateInventoryOpenState(boolean inventoryOpen) {
    User user = UserRepository.userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    if (!inventoryOpen && clientData.supportsInventoryAchievementPacket()) {
      this.forceInventoryOnClickOpen = true;
    }
    deactivateHand();
    this.inventoryOpen = inventoryOpen;
  }

  public boolean foodItem() {
    return foodItem;
  }
}