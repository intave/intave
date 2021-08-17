package de.jpx3.intave.tools.items;

import com.comphenix.protocol.utility.MinecraftVersion;
import com.google.common.collect.Lists;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.event.dispatch.PlayerAbilityTracker;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static de.jpx3.intave.adapter.MinecraftVersions.VER1_13_0;
import static de.jpx3.intave.adapter.MinecraftVersions.VER1_9_0;
import static de.jpx3.intave.tools.items.Enchantments.tridentRiptideEnchanted;

public final class ItemProperties {
  public static final Material ITEM_TRIDENT = materialByName("TRIDENT");
  private static final List<Material> materialUseItemList = Lists.newArrayList();
  private static final List<Material> materialSwordItemList = Lists.newArrayList();
  private static final List<Material> materialPotionList = Lists.newArrayList();
  private static final List<Material> foodLevelConstraintFoodItems = Lists.newArrayList();
  private static final List<Material> nonFoodLevelConstraintFoodItems = Lists.newArrayList();

  public static void setup() {
    try {
      MinecraftVersion serverVersion = ProtocolLibraryAdapter.serverVersion();
      loadDefaultUseItems(serverVersion);
      loadPotions();
      loadFoodItems();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void loadFoodItems() {
    List<String> foodLevelConstraintFoodItemNames = Lists.newArrayList(
      "apple", "bread", "porkchop", "cooked_porkchop",
      "pork", "grilled_pork", "cookie", "melon", "beef", "raw_beef",
      "cooked_beef", "chicken", "cooked_chicken", "rotten_flesh",
      "spider_eye", "baked_potato", "poisonous_potato", "golden_carrot",
      "pumpkin_pie", "rabbit", "cooked_rabbit", "mutton", "cooked_mutton",
      "mushroom_soup", "raw_fish", "cooked_fish", "raw_chicken",
      "carrot_item", "potato_item", "rabbit_stew"
    );

    List<String> nonFoodLevelConstraintFoodItemNames =
      Lists.newArrayList("golden_apple", "enchanted_golden_apple");

    materialListConvert(foodLevelConstraintFoodItemNames, foodLevelConstraintFoodItems);
    materialListConvert(nonFoodLevelConstraintFoodItemNames, nonFoodLevelConstraintFoodItems);
  }

  private static void materialListConvert(List<String> input, List<Material> output) {
    input.stream().map(ItemProperties::materialByName).forEach(output::add);
  }

  private static void loadDefaultUseItems(MinecraftVersion serverVersion) {
    if (serverVersion.isAtLeast(VER1_13_0)) {
      materialUseItemList.add(resolveTrident());
    }
    if (serverVersion.isAtLeast(VER1_9_0)) {
      materialUseItemList.add(resolveShield());
    }/* else {
      materialUseItemList.addAll(resolveSwords());
    }*/

    materialSwordItemList.addAll(resolveSwords());
    materialUseItemList.add(resolveBow());
  }

  private static void loadPotions() {
    materialPotionList.add(Material.POTION);
  }

  private static List<Material> resolveSwords() {
    return materialsMatching("SWORD");
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

  public static boolean canItemBeUsed(Player player, @Nullable ItemStack itemStack) {
    Material type = itemStack == null ? Material.AIR : itemStack.getType();
    if (ITEM_TRIDENT != null && type == ITEM_TRIDENT) {
      User user = UserRepository.userOf(player);
      return tridentUsable(user, itemStack);
    }

    // Bow check
    if (type == Material.BOW && !inventoryContains(player, Material.ARROW)) {
      return false;
    }

    boolean useItem = materialUseItemList.contains(type);
    boolean potion = materialPotionList.contains(type);
    return useItem || potion || foodConsumable(player, type);
  }

  public static boolean foodConsumable(Player player, Material type) {
    User user = UserRepository.userOf(player);
    AbilityMetadata abilityData = user.meta().abilities();
    boolean creative = abilityData.inGameMode(PlayerAbilityTracker.GameMode.CREATIVE);
    if (creative) {
      return false;
    }
    if (foodLevelConstraintFoodItems.contains(type)) {
      return user.player().getFoodLevel() < 20;
    }
    return nonFoodLevelConstraintFoodItems.contains(type);
  }

  public static boolean isSwordItem(Player player, @Nullable ItemStack itemStack) {
    Material type = itemStack == null ? Material.AIR : itemStack.getType();
    boolean itemSword = materialSwordItemList.contains(type);
    return itemSword;
  }

  private static boolean tridentUsable(User user, ItemStack itemStack) {
    Player player = user.player();
    World world = player.getWorld();
    MovementMetadata movementData = user.meta().movement();
    if (tridentRiptideEnchanted(itemStack)) {
      return movementData.inWater || (world.isThundering() || world.hasStorm());
    }
    return true;
  }

  private static List<Material> materialsMatching(String name) {
    return Arrays.stream(Material.values())
      .filter(material -> material.name().toLowerCase().contains(name.toLowerCase()))
      .collect(Collectors.toList());
  }

  private static Material materialByName(String name) {
    Material material = Material.getMaterial(name);
    if (material != null) {
      return material;
    }
    return Arrays.stream(Material.values())
      .filter(materiall -> materiall.name().equalsIgnoreCase(name))
      .findFirst().orElse(null);
  }

  private static boolean inventoryContains(Player player, Material item) {
    PlayerInventory inventory = player.getInventory();
    for (ItemStack content : inventory.getContents()) {
      if (content != null && content.getType() == item) {
        return true;
      }
    }
    return false;
  }
}