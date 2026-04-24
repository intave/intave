package de.jpx3.intave.module.filter;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;

import java.util.Collections;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.ENTITY_EQUIPMENT;

public final class EquipmentFilter extends Filter {
  private final IntavePlugin plugin;

  public EquipmentFilter(IntavePlugin plugin) {
    super("equipmentdata");
    this.plugin = plugin;
  }

  @PacketSubscription(
    packetsOut = {
      ENTITY_EQUIPMENT
    }
  )
  public void filterEquipment(ProtocolPacketEvent event, WrapperPlayServerEntityEquipment packet) {
    for (Equipment equipment : packet.getEquipment()) {
      ItemStack itemStack = SpigotConversionUtil.toBukkitItemStack(equipment.getItem());
      if (itemStack == null) {
        continue;
      }
      equipment.setItem(SpigotConversionUtil.fromBukkitItemStack(stripFromData(itemStack.clone())));
    }
    event.markForReEncode(true);
  }

  private ItemStack stripFromData(ItemStack itemStack) {
    if (itemStack == null) {
      return null;
    }
    itemStack.setAmount(1);

    if (itemStack.hasItemMeta()) {
      ItemMeta meta = itemStack.getItemMeta();
      if (meta.hasEnchants()) {
        for (Enchantment enchantment : itemStack.getEnchantments().keySet()) {
          itemStack.removeEnchantment(enchantment);
        }
        itemStack.addUnsafeEnchantment(Enchantment.THORNS, 1);
      }

      // taken from https://gist.github.com/dmulloy2/5d52ddbb89a1609dbea2
      if (meta instanceof BookMeta) {
        BookMeta bookMeta = (BookMeta) meta;
        bookMeta.setTitle(null);
        bookMeta.setPages(Collections.emptyList());
        bookMeta.setAuthor(null);
      } else if (meta instanceof EnchantmentStorageMeta) {
        EnchantmentStorageMeta enchantmentStorageMeta = (EnchantmentStorageMeta) meta;
        if (enchantmentStorageMeta.hasStoredEnchants()) {
          for (Enchantment ench : enchantmentStorageMeta.getStoredEnchants().keySet()) {
            enchantmentStorageMeta.removeStoredEnchant(ench);
          }
          enchantmentStorageMeta.addStoredEnchant(Enchantment.THORNS, 1, true);
        }
      } else if (meta instanceof FireworkEffectMeta) {
        ((FireworkEffectMeta) meta).setEffect(null);
      } else if (meta instanceof FireworkMeta) {
        FireworkMeta fireworkMeta = (FireworkMeta) meta;
        fireworkMeta.clearEffects();
        fireworkMeta.setPower(0);
      }
      //

      meta.setDisplayName("");
      if (meta.getLore() != null) {
        meta.setLore(Collections.emptyList());
      }
      meta.removeItemFlags(meta.getItemFlags().toArray(new ItemFlag[0]));
    }
    return itemStack;
  }

  @Override
  protected boolean enabled() {
//    if (MinecraftVersions.VER1_19.atOrAbove()) {
//      return false;
//    }
//    return !IntaveControl.GOMME_MODE && super.enabled();
    return false;
  }
}
