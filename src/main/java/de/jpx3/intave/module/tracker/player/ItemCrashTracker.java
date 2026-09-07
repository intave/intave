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

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.view.PacketEventsWindowItemView;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.UPDATE_SIGN;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.WINDOW_CLICK;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.SET_SLOT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.WINDOW_ITEMS;

public final class ItemCrashTracker extends Module {
  @PacketSubscription(
    packetsOut = {
      WINDOW_ITEMS, SET_SLOT
    }
  )
  public void checkOutgoingItems(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    ItemStack itemStack = packet.getItemModifier().readSafely(0);
    if (itemStack != null) {
      putOnWhitelist(user, itemStack);
    }
    ItemStack[] itemStacks = packet.getItemArrayModifier().readSafely(0);
    if (itemStacks != null && itemStacks.length != 0) {
      for (ItemStack stack : itemStacks) {
        if (stack != null) {
          putOnWhitelist(user, stack);
        }
      }
    }
    List<ItemStack> itemStackList = packet.getItemListModifier().readSafely(0);
    if (itemStackList != null) {
      for (ItemStack stack : itemStackList) {
        if (stack != null) {
          putOnWhitelist(user, stack);
        }
      }
    }
  }

  /**
   * PacketEvents entry point for the same two packets. The window content view already folds the
   * bulk ("window items") and the single slot ("set slot") layouts into one slot map plus the
   * carried item, which is exactly the set of stacks the ProtocolLib path walks through its three
   * item modifiers.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      WINDOW_ITEMS, SET_SLOT
    }
  )
  public void checkOutgoingItems(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    PacketEventsWindowItemView view = PacketEventsWindowItemView.of(event);
    if (view == null) {
      return;
    }
    User user = UserRepository.userOf(player);
    for (ItemStack stack : view.itemMap().values()) {
      if (stack != null) {
        putOnWhitelist(user, stack);
      }
    }
    if (view.carriedItemKnown() && view.carriedItem() != null) {
      putOnWhitelist(user, view.carriedItem());
    }
  }

  private void putOnWhitelist(User user, ItemStack stack) {
    InventoryMetadata inventory = user.meta().inventory();
    String name = ownerFromSkull(stack);
    if (name != null) {
      inventory.registerSkullRequest(name);
    }
  }

  private String ownerFromSkull(ItemStack skull) {
    String name = skull.getType().name();
    if (!(name.contains("SKULL") || name.contains("HEAD"))) {
      return null;
    }
    ItemMeta meta = skull.getItemMeta();
    if (meta instanceof SkullMeta) {
      return ownerFromSkullMeta((SkullMeta) meta);
    }
    return null;
  }

  private String ownerFromSkullMeta(SkullMeta meta) {
    return meta.getOwner();
  }

  /**
   * <h2>Why no PacketEvents twin is written</h2>
   * This guard measures the length of a <em>re-serialised</em> chat component, not of anything on
   * the wire. {@code getChatComponentArrays()} only binds on 1.8, where the sign update packet
   * still carries {@code IChatBaseComponent[]}; from 1.9 on the field is a {@code String[]}, the
   * safe read returns null and the whole check is inert. On 1.8 the wire caps each line at 384
   * characters, and the payload this kicks for is a line that is small on the wire but blows up
   * past 500 characters once NMS has parsed it and ProtocolLib has written it back out as JSON.
   * <p>
   * PacketEvents reads the same field with {@code readString(384)} and hands out the raw wire
   * string on every version, so {@code getTextLines()} can never reach the 500 character threshold
   * and a twin built on it would be a permanently dead check. Re-serialising the line through
   * PacketEvents' own Adventure GSON serializer is not the same measurement either: it is a
   * different writer with different output, so it would move the threshold in both directions and
   * could kick a player over a sign the ProtocolLib path accepts. There is no PacketEvents
   * accessor for an NMS-serialised chat component, so this stays ProtocolLib only.
   */
  @PacketSubscription(
    packetsIn = UPDATE_SIGN
  )
  public void checkSign(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    WrappedChatComponent[] wrappedChatComponents = packet.getChatComponentArrays().readSafely(0);

    if (wrappedChatComponents != null) {
      for (WrappedChatComponent chatComponent : wrappedChatComponents) {
        if (chatComponent.getJson().length() > 500) {
          event.setCancelled(true);
          user.kick("Too many characters in sign update packet");
          return;
        }
      }
    }
  }

  @PacketSubscription(
    packetsIn = {
      WINDOW_CLICK
    }
  )
  public void windowClickCrashFix(PacketEvent event) {
    if (windowClickCrashFix(UserRepository.userOf(event.getPlayer()))) {
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      WINDOW_CLICK
    }
  )
  public void windowClickCrashFix(PacketReceiveEvent event, Player player) {
    if (player == null) {
      return;
    }
    if (windowClickCrashFix(UserRepository.userOf(player))) {
      event.setCancelled(true);
    }
  }

  /**
   * Engine independent window click rate limiting.
   *
   * @return true when the packet has to be cancelled.
   */
  private boolean windowClickCrashFix(User user) {
    InventoryMetadata inventoryData = user.meta().inventory();
    if (System.currentTimeMillis() - inventoryData.lastWCCReset > 10000) {
      inventoryData.windowClickCounter = 0;
      inventoryData.lastWCCReset = System.currentTimeMillis();
    }

    if (inventoryData.windowClickCounter++ > 500 && FaultKicks.INVENTORY_FAULTS) {
      user.kick("Too many inventory interactions");
      return true;
    }
    return false;
  }
}
