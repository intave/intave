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

import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
  private boolean repeatedItemUseRequestThisTick;
  private boolean suspiciousNoSlowdownPacketPatternThisTick;
  public Material releaseItemType = Material.AIR;
  private boolean deactivateHandAtTickEnd;
  private boolean itemUseSlotSwitchConfirmedThisTick;
  private boolean viaVersionItemUseStateUncertain;
  private InterruptedItemUse slotSwitchInterruptedItemUse;
  private int heldItemSlotChangesThisTick;
  public volatile SlotSwitchData slotSwitchData;
  public int pastSlotSwitch;
  private boolean inventoryOpen;
  private int handSlot;
  private ItemStack previousSpearThisTick;
  private volatile boolean handActive;
  private volatile PlayerHand activeHand;
  private volatile long activeItemUseSequence;
  private long clientConfirmedItemUseSequence = -1;
  private long serverCompletedItemUseSequence = -1;
  private final Lock handActiveLock = new ReentrantLock();
  private Material activeItemType;
  private boolean activeItemAllowsSprinting;
  private float activeItemUseSpeedMultiplier = 0.2F;
  private List<String> items = new ArrayList<>();
  private boolean foodItem;
  private volatile String itemUsePermissionDebug = "UNKNOWN";
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

  public void noteUseItemMovementSync() {
    if (user.meta().protocol().viaVersionShieldBlockReplacement()) {
      viaVersionItemUseStateUncertain = true;
    }
  }

  public boolean viaVersionItemUseStateUncertain() {
    return viaVersionItemUseStateUncertain;
  }

  public void clearViaVersionItemUseStateUncertainty() {
    viaVersionItemUseStateUncertain = false;
  }

  public String itemUsePermissionDebug() {
    return itemUsePermissionDebug;
  }

  public void itemUsePermissionDebug(String permission) {
    itemUsePermissionDebug = permission;
  }

  public PlayerHand activeHand() {
    if (activeHand != null) {
      return activeHand;
    }
    if (handActive && activeItemType != Material.AIR
      && activeItemType == offhandItemType()
      && activeItemType != heldItemType()) {
      return PlayerHand.OFF_HAND;
    }
    return offhandItemPrimary() ? PlayerHand.OFF_HAND : PlayerHand.MAIN_HAND;
  }

  public boolean activeItemAllowsSprinting() {
    return activeItemAllowsSprinting;
  }

  public float activeItemUseSpeedMultiplier() {
    return activeItemUseSpeedMultiplier;
  }

  public long activeItemUseSequence() {
    return activeItemUseSequence;
  }

  public int activeClientInventorySlot(int windowId) {
    if (!handActive()) {
      return -1;
    }
    if (windowId == -2) {
      return activeHand() == PlayerHand.OFF_HAND ? 40 : handSlot;
    }
    if (windowId != 0) {
      return -1;
    }
    return activeHand() == PlayerHand.OFF_HAND ? 45 : 36 + handSlot;
  }

  public boolean applyClientActiveItemReplacement(
    long expectedSequence, @Nullable Material replacementType
  ) {
    handActiveLock.lock();
    try {
      if (!handActive || activeItemUseSequence != expectedSequence) {
        return false;
      }
      Material type = replacementType == null ? Material.AIR : replacementType;
      // 1.8-1.13 retain the exact ItemStack object captured at use start, so
      // any clientbound replacement ends use. 1.14+ compare only the item type.
      if (serverCompletedItemUseSequence == expectedSequence
        || user.meta().protocol().protocolVersion() < ProtocolMetadata.VER_1_14
        || activeItemType != type) {
        deactivateHand();
      }
      return true;
    } finally {
      handActiveLock.unlock();
    }
  }

  public void markServerItemUseCompleted() {
    handActiveLock.lock();
    try {
      if (handActive) {
        serverCompletedItemUseSequence = activeItemUseSequence;
      }
    } finally {
      handActiveLock.unlock();
    }
  }

  public void applyClientItemUseFlags(long expectedSequence, boolean usingItem) {
    handActiveLock.lock();
    try {
      if (!handActive || activeItemUseSequence != expectedSequence) {
        return;
      }
      if (usingItem) {
        clientConfirmedItemUseSequence = expectedSequence;
      } else if (clientConfirmedItemUseSequence == expectedSequence
        || serverCompletedItemUseSequence == expectedSequence) {
        deactivateHand();
      }
    } finally {
      handActiveLock.unlock();
    }
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
  public ItemStack itemInHand(@Nullable PlayerHand hand) {
    return hand == PlayerHand.OFF_HAND ? offhandItem() : heldItem();
  }

  public boolean usableItemInEitherHand() {
    return ItemProperties.canItemBeUsed(user, heldItem()) ||
      ItemProperties.canItemBeUsed(user, offhandItem());
  }

  public boolean usableItemInEitherHandOrHotbar() {
    return !usableItemProfilesInEitherHandOrHotbar().isEmpty();
  }

  public List<ItemProperties.ItemUseProfile> usableItemProfilesInEitherHandOrHotbar() {
    if (player == null) {
      return new ArrayList<>();
    }
    Set<ItemProperties.ItemUseProfile> profiles = new LinkedHashSet<>();
    addItemUseProfile(profiles, heldItem());
    addItemUseProfile(profiles, offhandItem());
    for (int i = 0; i < 9; i++) {
      addItemUseProfile(profiles, player.getInventory().getItem(i));
    }
    return new ArrayList<>(profiles);
  }

  private void addItemUseProfile(
    Set<? super ItemProperties.ItemUseProfile> profiles, @Nullable ItemStack itemStack
  ) {
    ItemProperties.ItemUseProfile profile = ItemProperties.itemUseProfile(user, itemStack);
    if (profile != null) {
      profiles.add(profile);
    }
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

  public void recordPreviousHeldItemForReach(ItemStack item) {
    previousSpearThisTick = item != null && item.getAmount() > 0
      && item.getType().name().endsWith("_SPEAR")
      ? item.clone() : null;
  }

  @Nullable
  public ItemStack previousSpearThisTick() {
    return previousSpearThisTick;
  }

  public void clearPreviousSpearThisTick() {
    previousSpearThisTick = null;
  }

  public Material heldItemType() {
    ItemStack heldItem = heldItem();
    return heldItem == null || heldItem.getAmount() == 0 ? Material.AIR : heldItem.getType();
  }

  public boolean inventoryOpen() {
    return inventoryOpen;
  }

  public void activateHand() {
    activateHandIfUsable(null);
  }

  public boolean activateHandIfUsable(@Nullable PlayerHand hand) {
    return activateHandIfUsable(hand, false);
  }

  public boolean activateHandIfUsable(
    @Nullable PlayerHand hand, boolean useOnBlock
  ) {
    ItemStack heldItem = heldItem();
    ItemStack offhandItem = offhandItem();
    boolean offHandUsable = ItemProperties.canItemBeUsed(user, offhandItem);
    boolean mainHandUsable = ItemProperties.canItemBeUsed(user, heldItem);
    PlayerHand requestedHand = hand != null ? hand
      : offHandUsable && !mainHandUsable
      ? PlayerHand.OFF_HAND : PlayerHand.MAIN_HAND;
    ItemStack requestedItem = requestedHand == PlayerHand.OFF_HAND
      ? offhandItem : heldItem;
    ItemProperties.ItemUseProfile profile = ItemProperties.itemUseProfile(
      user, requestedItem
    );
    if (profile == null || useOnBlock
      && !ItemProperties.startsUsingOnBlock(user, requestedItem)) {
      return false;
    }
    if (handActive() && handActiveTicks > 0) {
      // Minecraft suppresses further use attempts while either hand is busy.
      // Once the initial use has crossed a movement boundary, another usable-
      // item request without a release comes from packet-level item-use wrapping
      // rather than held right click. Keep same-tick hand fallbacks legitimate.
      repeatedItemUseRequestThisTick = true;
      suspiciousNoSlowdownPacketPatternThisTick = true;
    }
    MovementMetadata movement = user.meta().movement();
    if (!user.meta().protocol().combatUpdate()
      && handActive() && slotSwitchData != null && releaseItemNextTick) {
      // In 1.8, the hotbar change clears the old ItemStack use. On the next
      // client tick C09 is sent first, then a held use key sends C08 for the
      // newly selected item before movement. That C08 starts a new use and
      // supersedes the release scheduled for the old stack.
      clearItemUseSearchTransitions(movement);
      restartHandAfterSlotSwitch(requestedHand, requestedItem, profile);
      return true;
    }
    if (handActive()) {
      // A use request cannot restart active item use. In particular, it must
      // not clear the pending unannounced-slot-switch branch: doing so would
      // let repeated forged use packets create a fresh no-slow branch on each
      // movement tick without ever confirming the switch with a slot packet.
      return true;
    }
    clearItemUseSearchTransitions(movement);
    activateHand(requestedHand, requestedItem, profile);
    return true;
  }

  private void clearItemUseSearchTransitions(MovementMetadata movement) {
    movement.setLegacyItemUseForcedReleaseApplied(false);
    movement.setLegacyItemUseReleaseUncertain(false);
    movement.setLegacyItemUseSlotSwitchPending(false);
  }

  private void restartHandAfterSlotSwitch(
    PlayerHand hand,
    ItemStack item,
    ItemProperties.ItemUseProfile profile
  ) {
    handActiveLock.lock();
    try {
      releaseItemNextTick = false;
      releaseItemType = Material.AIR;
      deactivateHandAtTickEnd = false;
      handActive = false;
      activeHand = null;
      activeItemType = Material.AIR;
      activeItemAllowsSprinting = false;
      activeItemUseSpeedMultiplier = 0.2F;
      foodItem = false;
      handActiveTicks = 0;
      pastItemUsageTransition = 0;
    } finally {
      handActiveLock.unlock();
    }
    activateHand(hand, item, profile);
  }

  public void activateHand(@Nullable PlayerHand hand) {
    activateHandIfUsable(hand);
  }

  private void activateHand(
    PlayerHand hand,
    ItemStack item,
    ItemProperties.ItemUseProfile profile
  ) {
    handActiveLock.lock();
    try {
      if (handActive) {
        return;
      }
      this.handActive = true;
      this.activeItemUseSequence++;
      this.clientConfirmedItemUseSequence = -1;
      this.serverCompletedItemUseSequence = -1;
      this.activeHand = hand;
      this.activeItemType = item.getType();
      this.activeItemAllowsSprinting = profile.canSprint();
      this.activeItemUseSpeedMultiplier = profile.speedMultiplier();

      user.meta().movement().setLegacyItemUseSlotSwitchPending(false);
      user.meta().movement().handItemSimulationFails = 0;

      if (activeHand == PlayerHand.OFF_HAND) {
        this.foodItem = ItemProperties.foodConsumable(user, offhandItem());
      } else {
        this.foodItem = ItemProperties.foodConsumable(user, heldItem());
      }
      this.pastItemUsageTransition = 0;
      this.handActiveTicks = 0;
      this.activatedItemThisTick = true;
      if (deactivatedItemThisTick) {
        suspiciousNoSlowdownPacketPatternThisTick = true;
      }

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
      deactivateHandAtTickEnd = false;
      User user = UserRepository.userOf(player);
      MovementMetadata movement = user.meta().movement();
      movement.setLegacyItemUseSlotSwitchPending(false);
      movement.setLegacyItemUseReleaseUncertain(false);
      movement.setLegacyItemUseForcedReleaseApplied(false);
      if (!handActive) {
        activeHand = null;
        activeItemAllowsSprinting = false;
        activeItemUseSpeedMultiplier = 0.2F;
        return;
      }
      ItemStack heldItem = heldItem();
      ItemStack offhandItem = offhandItem();
      if ((heldItem != null && Enchantments.tridentRiptideEnchanted(heldItem))
        || (offhandItem != null && Enchantments.tridentRiptideEnchanted(offhandItem))) {
        movement.activeTick(RIPTIDE_SPIN);
        movement.highestLocalRiptideLevel = Math.max(
          movement.highestLocalRiptideLevel,
          Math.max(Enchantments.resolveRiptideModifier(heldItem), Enchantments.resolveRiptideModifier(offhandItem))
        );
        movement.onGroundWithRiptide = movement.onGround;
      }
      this.handActive = false;
      this.activeItemUseSequence++;
      this.clientConfirmedItemUseSequence = -1;
      this.serverCompletedItemUseSequence = -1;
      this.pastItemUsageTransition = 0;
      this.handActiveTicks = 0;
      this.deactivatedItemThisTick = true;
      if (activatedItemThisTick) {
        suspiciousNoSlowdownPacketPatternThisTick = true;
      }
      Material activeItem = this.activeItemType;
      if (IntaveControl.DEBUG_ITEM_USAGE) {
        user.sendMessage("Item usage ended: " + activeItem);
//        Thread.dumpStack();
        System.out.println("Item usage ended: " + activeItem);
      }
      this.activeItemType = Material.AIR;
      this.activeHand = null;
      this.activeItemAllowsSprinting = false;
      this.activeItemUseSpeedMultiplier = 0.2F;
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
    releaseItemType = activeItemType;
  }

  /**
   * Keeps the pre-release item-use state available to movement physics and
   * applies the scheduled release once an observable movement tick has finished.
   * A bare FLYING packet cannot expose item slowdown, so it leaves the reset
   * pending instead of needlessly resetting the client's held-item animation.
   */
  @Nullable
  public Material deferScheduledItemReleaseUntilTickEnd(boolean hasMovement) {
    if (!releaseItemNextTick || !hasMovement) {
      return null;
    }
    Material scheduledItemType = releaseItemType;
    releaseItemNextTick = false;
    releaseItemType = Material.AIR;
    deactivateHandAtTickEnd = true;
    return scheduledItemType;
  }

  public void updateSlotSwitch() {
    if (slotSwitchData != null) {
      int slot = slotSwitchData.slot();
      ItemStack item = slotSwitchData.item();

      if (user.meta().protocol().combatUpdate()) {
        setHeldItemSlot(slot);
        pastHotBarSlotChange = 0;
        slotSwitchData = null;
        return;
      }

      boolean primaryItemUsable = ItemProperties.canItemBeUsed(user, item);
      boolean offhandItemUsage = ItemProperties.canItemBeUsed(user, offhandItem());
      if ((!primaryItemUsable && !offhandItemUsage) || !handActive()) {
        deactivateHand();
      }
      setHeldItemSlot(slot);
      pastHotBarSlotChange = 0;
      slotSwitchData = null;
    }
  }

  public void setHeldItemSlot(int slot) {
    this.handSlot = slot;
  }

  public void receiveHeldItemSlot(int slot, @Nullable ItemStack item) {
    boolean changed = slot != handSlot;
    if (changed) {
      MovementMetadata movement = user.meta().movement();
      heldItemSlotChangesThisTick++;
      itemUseSlotSwitchConfirmedThisTick |= movement.legacyItemUseSlotSwitchPending();
      movement.setLegacyItemUseSlotSwitchPending(false);
    }
    pastSlotSwitch = 0;
    // LivingEntity checks stack identity through 1.13, but item type from 1.14.
    // A same-type switch therefore keeps local use active until the client's
    // synchronized metadata ends it, which can take several movement ticks.
    boolean keepsUsingSameItem = user.meta().protocol().protocolVersion() >= ProtocolMetadata.VER_1_14
      && item != null && item.getAmount() > 0 && item.getType() == activeItemType;
    if (changed && handActive() && activeHand() == PlayerHand.MAIN_HAND
      && !keepsUsingSameItem) {
      // The selected-slot packet confirms that the client changed its main-hand
      // stack. EntityPlayer#onUpdate clears the old use by stack identity before
      // movement. Track that transition directly; injecting RELEASE_USE_ITEM and
      // updateInventory here would reset the newly selected item client-side.
      // Offhand use is unaffected by a hotbar change.
      if (slotSwitchInterruptedItemUse == null) {
        slotSwitchInterruptedItemUse = new InterruptedItemUse(
          handSlot,
          activeItemType,
          activeItemAllowsSprinting,
          activeItemUseSpeedMultiplier,
          foodItem,
          handActiveTicks,
          pastItemUsageTransition
        );
      }
      deactivateHand();
    }
    setHeldItemSlot(slot);
    slotSwitchData = new SlotSwitchData(slot, item);
    if (changed && heldItemSlotChangesThisTick > 1
      && slotSwitchInterruptedItemUse != null
      && slot == slotSwitchInterruptedItemUse.slot) {
      restoreItemUseInterruptedBySlotSwitch(slotSwitchInterruptedItemUse);
    }
  }

  private void restoreItemUseInterruptedBySlotSwitch(InterruptedItemUse interrupted) {
    handActiveLock.lock();
    try {
      if (handActive) {
        return;
      }
      handActive = true;
      activeItemUseSequence++;
      clientConfirmedItemUseSequence = -1;
      serverCompletedItemUseSequence = -1;
      activeHand = PlayerHand.MAIN_HAND;
      activeItemType = interrupted.itemType;
      activeItemAllowsSprinting = interrupted.allowsSprinting;
      activeItemUseSpeedMultiplier = interrupted.speedMultiplier;
      foodItem = interrupted.foodItem;
      handActiveTicks = interrupted.handActiveTicks;
      pastItemUsageTransition = interrupted.pastItemUsageTransition;
      deactivateHandAtTickEnd = false;
      activatedItemThisTick = true;
      suspiciousNoSlowdownPacketPatternThisTick = true;

      // A vanilla client sends at most one carried-slot update per client tick.
      // Returning to the original slot before movement is therefore a packet-
      // injected switch burst, not a second legitimate item-use transition.
      // Preserve the original use state and consume the one speculative
      // unannounced-switch branch so physics must apply item slowdown.
      user.meta().movement().setLegacyItemUseSlotSwitchPending(true);
    } finally {
      handActiveLock.unlock();
    }
  }

  public boolean itemUseSlotSwitchConfirmedThisTick() {
    return itemUseSlotSwitchConfirmedThisTick;
  }

  public boolean itemUseInterruptedBySlotSwitchThisTick() {
    return slotSwitchInterruptedItemUse != null;
  }

  public boolean interruptedItemAllowsSprinting() {
    return slotSwitchInterruptedItemUse != null
      && slotSwitchInterruptedItemUse.allowsSprinting;
  }

  public float interruptedItemUseSpeedMultiplier() {
    return slotSwitchInterruptedItemUse == null
      ? 0.2F : slotSwitchInterruptedItemUse.speedMultiplier;
  }

  public boolean repeatedItemUseRequestThisTick() {
    return repeatedItemUseRequestThisTick;
  }

  public boolean suspiciousNoSlowdownPacketPatternThisTick() {
    return suspiciousNoSlowdownPacketPatternThisTick;
  }

  public void markSuspiciousNoSlowdownPacketPatternThisTick() {
    suspiciousNoSlowdownPacketPatternThisTick = true;
  }

  public void clearItemUseTickTransitions() {
    activatedItemThisTick = false;
    deactivatedItemThisTick = false;
    repeatedItemUseRequestThisTick = false;
    // Keep slot-switch ordering available to movement physics. tickComplete()
    // clears it after the packet has selected its simulation branch.
  }

  @Deprecated
  public void setHandActive(boolean handActive) {
    this.handActive = handActive;
    this.activeItemUseSequence++;
    this.clientConfirmedItemUseSequence = -1;
    this.serverCompletedItemUseSequence = -1;
    if (!handActive) {
      this.activeHand = null;
    }
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
    boolean deactivatedItemThisTick,
    @Nullable PlayerHand activeHand,
    boolean activeItemAllowsSprinting,
    float activeItemUseSpeedMultiplier
  ) {
    this.handSlot = heldSlot;
    this.handActive = handActive;
    this.activeItemUseSequence++;
    this.clientConfirmedItemUseSequence = -1;
    this.serverCompletedItemUseSequence = -1;
    this.activeHand = activeHand;
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
    this.activeItemAllowsSprinting = activeItemAllowsSprinting;
    this.activeItemUseSpeedMultiplier = activeItemUseSpeedMultiplier;
    this.itemUseSlotSwitchConfirmedThisTick = false;
    this.viaVersionItemUseStateUncertain = false;
    this.repeatedItemUseRequestThisTick = false;
    this.suspiciousNoSlowdownPacketPatternThisTick = false;
    this.slotSwitchInterruptedItemUse = null;
    this.heldItemSlotChangesThisTick = 0;
  }

  public void updateInventoryOpenState(boolean inventoryOpen) {
    User user = UserRepository.userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    if (!inventoryOpen && clientData.supportsInventoryAchievementPacket()) {
      this.forceInventoryOnClickOpen = true;
    }
    if (inventoryOpen != this.inventoryOpen) {
      releaseItemNextTick();
      if (user.receives(MessageChannel.DEBUG_ITEM_USE)) {
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
    if (deactivateHandAtTickEnd) {
      deactivateHandAtTickEnd = false;
      boolean wasHandActive = handActive();
      deactivateHand();
      if (wasHandActive && !user.meta().protocol().combatUpdate()) {
        user.meta().movement().setLegacyItemUseReleaseUncertain(true);
        user.meta().movement().setLegacyItemUseForcedReleaseApplied(false);
      }
    }
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
    itemUseSlotSwitchConfirmedThisTick = false;
    suspiciousNoSlowdownPacketPatternThisTick = false;
    slotSwitchInterruptedItemUse = null;
    heldItemSlotChangesThisTick = 0;
  }

  public boolean couldChargeCrossbow() {
    return ItemProperties.canChargeCrossbow(user, heldItem())
      || ItemProperties.canChargeCrossbow(user, offhandItem());
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

  private static final class InterruptedItemUse {
    private final int slot;
    private final Material itemType;
    private final boolean allowsSprinting;
    private final float speedMultiplier;
    private final boolean foodItem;
    private final int handActiveTicks;
    private final int pastItemUsageTransition;

    private InterruptedItemUse(
      int slot,
      Material itemType,
      boolean allowsSprinting,
      float speedMultiplier,
      boolean foodItem,
      int handActiveTicks,
      int pastItemUsageTransition
    ) {
      this.slot = slot;
      this.itemType = itemType;
      this.allowsSprinting = allowsSprinting;
      this.speedMultiplier = speedMultiplier;
      this.foodItem = foodItem;
      this.handActiveTicks = handActiveTicks;
      this.pastItemUsageTransition = pastItemUsageTransition;
    }
  }
}
