package de.jpx3.intave.user.meta;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.player.Enchantments;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
  public int pastSlotSwitch;
  private boolean inventoryOpen;
  private int handSlot;
  private volatile boolean handActive;
  private final Lock handActiveLock = new ReentrantLock();
  private Material activeItemType;
  private boolean foodItem;

  public InventoryMetadata(Player player) {
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
    return ItemProperties.canItemBeUsed(player, heldItem()) || ItemProperties.canItemBeUsed(player, offhandItem());
  }

  @Nullable
  public Material offhandItemType() {
    ItemStack item = offhandItem();
    return item == null || item.getAmount() == 0 ? Material.AIR : item.getType();
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
      if (handActive) {
        return;
      }
      this.handActive = true;
      this.foodItem = ItemProperties.foodConsumable(player, heldItemType());
      this.pastItemUsageTransition = 0;
      this.handActiveTicks = 0;
      this.activeItemType = heldItemType();
      if (IntaveControl.DEBUG_ITEM_USAGE) {
        Material activeItem = this.activeItemType;
        Synchronizer.synchronize(() -> {
          player.sendMessage("Item usage started: " + activeItem);
        });
        Thread.dumpStack();
        System.out.println("Item usage started: " + this.activeItemType);
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
      if (heldItem != null && Enchantments.tridentRiptideEnchanted(heldItem)
        || offhandItem != null && Enchantments.tridentRiptideEnchanted(offhandItem)) {
        movementData.pastRiptideSpin = 0;
        movementData.highestLocalRiptideLevel = Math.max(
          movementData.highestLocalRiptideLevel,
          Math.max(Enchantments.resolveRiptideModifier(heldItem), Enchantments.resolveRiptideModifier(offhandItem))
        );
        movementData.onGroundWithRiptide = movementData.onGround;
      }
      this.handActive = false;
      this.pastItemUsageTransition = 0;
      this.handActiveTicks = 0;
      if (IntaveControl.DEBUG_ITEM_USAGE) {
        Material activeItem = this.activeItemType;
        Synchronizer.synchronize(() -> {
          player.sendMessage("Item usage ended: " + activeItem);
        });
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
    releaseItemNextTick = true;
    releaseItemType = heldItemType();
  }

  public void setHeldItemSlot(int slot) {
    this.handSlot = slot;
  }

  @Deprecated
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
