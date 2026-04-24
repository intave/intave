package de.jpx3.intave.module.tracker.player;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

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
    if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
      putOnWhitelist(user, SpigotConversionUtil.toBukkitItemStack(new WrapperPlayServerSetSlot((PacketSendEvent) event).getItem()));
    } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
      for (com.github.retrooper.packetevents.protocol.item.ItemStack stack : new WrapperPlayServerWindowItems((PacketSendEvent) event).getItems()) {
        putOnWhitelist(user, SpigotConversionUtil.toBukkitItemStack(stack));
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
    if (skull == null) {
      return null;
    }
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
    String[] textLines = new WrapperPlayClientUpdateSign((com.github.retrooper.packetevents.event.PacketReceiveEvent) event).getTextLines();
    if (textLines != null) {
      for (String line : textLines) {
        if (line != null && line.length() > 500) {
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
