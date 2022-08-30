package de.jpx3.intave.user.meta;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.annotate.refactoring.IdoNotBelongHere;
import de.jpx3.intave.player.Enchantments;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

@Relocate
public final class InventoryMetadata {
  private final Player player;
  private final List<String> whitelistedItemIdRequests = new ArrayList<>();
  public int handActiveTicks, pastHandActiveTicks = 100;
  public int pastItemUsageTransition;
  public int pastHotBarSlotChange;
  public long lastWCCReset;
  public int windowClickCounter;
  public boolean forceInventoryOnClickOpen = true;
  public boolean blockNextArrow = false;
  public boolean releaseItemNextTick = false;
  public Material releaseItemType = Material.AIR;
  public volatile SlotSwitchData slotSwitchData;
  private int handSlot;
  private boolean handActive;
  private Material activeItem;
  private boolean foodItem;
  private boolean inventoryOpen;

  public InventoryMetadata(Player player) {
    this.player = player;
    if (player != null) {
      this.handSlot = player.getInventory().getHeldItemSlot();
    }
    activeItem = Material.AIR;
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

  @Nullable
  public Material offhandItemType() {
    ItemStack item = offhandItem();
    return item == null ? null : item.getType();
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
    ItemStack offhandItem = offhandItem();
    if (heldItem != null && Enchantments.tridentRiptideEnchanted(heldItem)
        || offhandItem != null && Enchantments.tridentRiptideEnchanted(offhandItem)) {
      movementData.pastRiptideSpin = 0;
      movementData.onGroundWithRiptide = movementData.onGround;
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

  public void releaseItemNextTick() {
    releaseItemNextTick = true;
    releaseItemType = heldItemType();
  }

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
