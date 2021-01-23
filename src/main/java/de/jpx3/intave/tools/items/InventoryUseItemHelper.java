package de.jpx3.intave.tools.items;

import com.comphenix.protocol.utility.MinecraftVersion;
import com.google.common.collect.Lists;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.tools.annotate.Nullable;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static de.jpx3.intave.adapter.ProtocolLibAdapter.AQUATIC_UPDATE;
import static de.jpx3.intave.adapter.ProtocolLibAdapter.COMBAT_UPDATE;
import static de.jpx3.intave.tools.items.BukkitItemResolver.materialByName;
import static de.jpx3.intave.tools.items.BukkitItemResolver.materialsByName;
import static de.jpx3.intave.tools.items.PlayerEnchantmentHelper.tridentRiptideEnchanted;

public final class InventoryUseItemHelper {
  public static final Material ITEM_TRIDENT = materialByName("TRIDENT");
  private static final List<Material> materialUseItemList = Lists.newArrayList();
  private static final List<Material> materialSwordItemList = Lists.newArrayList();
  private static final List<Material> materialPotionList = Lists.newArrayList();
  private static FoodItemsRegistry foodItemRegistry;

  public static void setup() {
    try {
      MinecraftVersion serverVersion = ProtocolLibAdapter.serverVersion();
      loadDefaultUseItems(serverVersion);
      loadPotions();
      loadFoodItems();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void loadFoodItems() {
    foodItemRegistry = new FoodItemsRegistry();
  }

  private static void loadDefaultUseItems(MinecraftVersion serverVersion) {
    if (serverVersion.isAtLeast(AQUATIC_UPDATE)) {
      materialUseItemList.add(resolveTrident());
    }
    if (serverVersion.isAtLeast(COMBAT_UPDATE)) {
      materialUseItemList.add(resolveShield());
    } else {
      materialUseItemList.addAll(resolveSwords());
    }

    materialSwordItemList.addAll(resolveSwords());
    materialUseItemList.add(resolveBow());
  }

  private static void loadPotions() {
    materialPotionList.add(Material.POTION);
  }

  private static List<Material> resolveSwords() {
    return materialsByName("SWORD");
  }

  private static Material resolveTrident() {
    return materialByName("TRIDENT");
  }

  private static Material resolveShield() {
    return materialByName("SHIELD");
  }

  private static Material resolveBow() {
    return Material.BOW;
  }

  public static boolean isUseItem(Player player, @Nullable ItemStack itemStack) {
    Material type = itemStack == null ? Material.AIR : itemStack.getType();
    if (ITEM_TRIDENT != null && type == ITEM_TRIDENT) {
      User user = UserRepository.userOf(player);
      return tridentUsable(user, itemStack);
    }
    boolean useItem = materialUseItemList.contains(type);
    boolean potion = materialPotionList.contains(type);
    return useItem || potion || foodItemRegistry.foodConsumable(player.getFoodLevel(), type);
  }

  public static boolean isSwordItem(Player player, @Nullable ItemStack itemStack) {
    Material type = itemStack == null ? Material.AIR : itemStack.getType();
    boolean itemSword = materialSwordItemList.contains(type);
    return itemSword;
  }

  private static boolean tridentUsable(User user, ItemStack itemStack) {
    Player player = user.player();
    World world = player.getWorld();
    UserMetaMovementData movementData = user.meta().movementData();
    if (tridentRiptideEnchanted(itemStack)) {
      return movementData.inWater || (world.isThundering() || world.hasStorm());
    }
    return true;
  }

  public static FoodItemsRegistry foodItemRegistry() {
    return foodItemRegistry;
  }
}