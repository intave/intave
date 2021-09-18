package de.jpx3.intave.module.filter;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
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
  public void filterEquipment(PacketEvent event) {
    PacketContainer packet = event.getPacket();

//    if (packet.getItemModifier().readSafely(0) != null) {
//      // 1.8 - 1.15
//      ItemStack itemStack = packet.getItemModifier().readSafely(0);
//      ItemStack newItemStack = stripFromData(itemStack);
//      packet.getItemModifier().write(0, newItemStack);
//    } else {
//      // 1.16+
//      //noinspection unchecked
//      List<Pair<?, ?>> slotItemPairList = (List<Pair<?, ?>>) packet.getModifier().readSafely(1);
//      EquivalentConverter<ItemStack> converter = BukkitConverters.getItemStackConverter();
//      for (int i = 0; i < slotItemPairList.size(); i++) {
//        Pair<?, ?> pair = slotItemPairList.get(i)
//          .mapSecond(o -> converter.getGeneric(stripFromData(converter.getSpecific(o))));
//        slotItemPairList.set(i, pair);
//      }
//    }
  }

  private ItemStack stripFromData(ItemStack itemStack) {
    itemStack.setAmount(1);
    itemStack.setDurability((short) 1337);

    if (itemStack.hasItemMeta()) {
      ItemMeta meta = itemStack.getItemMeta();
      if (meta.hasEnchants()) {
        for (Enchantment enchantment : itemStack.getEnchantments().keySet()) {
          itemStack.removeEnchantment(enchantment);
        }
        itemStack.addUnsafeEnchantment(Enchantment.LURE, 1);
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

      meta.setDisplayName("Intave");
      meta.setLore(Collections.singletonList("Intave"));
      meta.removeItemFlags(meta.getItemFlags().toArray(new ItemFlag[0]));
    }
    return itemStack;
  }

  @Override
  protected boolean enabled() {
    return false;
  }
}
