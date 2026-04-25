package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
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

public class ItemCrashTracker extends Module {
  @PacketSubscription(
    packetsOut = {
      WINDOW_ITEMS, SET_SLOT
    }
  )
  public void checkOutgoingItems(ProtocolPacketEvent event) {
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

  @PacketSubscription(
    packetsIn = UPDATE_SIGN
  )
  public void checkSign(ProtocolPacketEvent event) {
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
  public void windowClickCrashFix(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();
    if (System.currentTimeMillis() - inventoryData.lastWCCReset > 10000) {
      inventoryData.windowClickCounter = 0;
      inventoryData.lastWCCReset = System.currentTimeMillis();
    }

    if (inventoryData.windowClickCounter++ > 500 && FaultKicks.INVENTORY_FAULTS) {
      user.kick("Too many inventory interactions");
      event.setCancelled(true);
    }
  }
}
