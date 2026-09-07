package de.jpx3.intave.module.filter;

import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.view.EntityEquipmentView;
import de.jpx3.intave.packet.view.PacketEventsEntityEquipmentView;
import de.jpx3.intave.packet.view.ProtocolLibEntityEquipmentView;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;

import java.util.Collections;
import java.util.List;

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
    filterEquipment(new ProtocolLibEntityEquipmentView(event));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      ENTITY_EQUIPMENT
    }
  )
  public void filterEquipment(PacketSendEvent event) {
    PacketEventsEntityEquipmentView view = PacketEventsEntityEquipmentView.of(event);
    if (view == null) {
      return;
    }
    filterEquipment(view);
  }

  /** Engine independent equipment stripping; see {@link EntityEquipmentView}. */
  private void filterEquipment(EntityEquipmentView view) {
    List<ItemStack> items = view.items();
    for (int index = 0; index < items.size(); index++) {
      ItemStack itemStack = items.get(index);
      if (itemStack == null) {
        continue;
      }
      view.setItem(index, stripFromData(itemStack));
    }
    view.release();
  }

  private ItemStack stripFromData(ItemStack itemStack) {
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
