package de.jpx3.intave.player;

import com.comphenix.protocol.utility.MinecraftVersion;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static de.jpx3.intave.adapter.MinecraftVersions.VER1_13_0;
import static de.jpx3.intave.adapter.MinecraftVersions.VER1_9_0;
import static de.jpx3.intave.player.Enchantments.tridentRiptideEnchanted;

public final class ItemProperties {
  public static final Material ITEM_TRIDENT = materialByName("TRIDENT");
  public static final Material CROSSBOW = materialByName("CROSSBOW");
  private static final Set<Material> materialUseItems = Sets.newHashSet();
  private static final Set<Material> materialSwordItems = Sets.newHashSet();
  private static final Set<Material> materialTrapdoorItems = Sets.newHashSet();
  private static final Set<Material> materialPotionItems = Sets.newHashSet();
  private static final Set<Material> foodLevelConstraintFoodItems = Sets.newHashSet();
  private static final Set<Material> nonFoodLevelConstraintFoodItems = Sets.newHashSet();
  private static final Set<Material> arrowItems = Sets.newHashSet();

  public static void setup() {
    try {
      MinecraftVersion serverVersion = ProtocolLibraryAdapter.serverVersion();
      loadDefaultUseItems(serverVersion);
      loadPotions();
      loadFoodItems();
      loadArrows();
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
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

  private static void materialListConvert(List<String> input, Set<? super Material> output) {
    input.stream().map(ItemProperties::materialByName).forEach(output::add);
  }

  private static void loadArrows() {
    arrowItems.addAll(materialsMatching("ARROW"));
  }

  private static void loadDefaultUseItems(MinecraftVersion serverVersion) {
    if (serverVersion.isAtLeast(VER1_13_0)) {
      materialUseItems.add(materialByName("TRIDENT"));
    }
    if (serverVersion.isAtLeast(VER1_9_0)) {
      materialUseItems.add(materialByName("SHIELD"));
    }
    materialTrapdoorItems.addAll(materialsMatching("TRAP_DOOR"));
    materialTrapdoorItems.addAll(materialsMatching("TRAPDOOR"));
    materialSwordItems.addAll(materialsMatching("SWORD"));
    materialUseItems.add(Material.BOW);
  }

  private static void loadPotions() {
    materialPotionItems.add(Material.POTION);
    materialPotionItems.add(materialByName("SPLASH_POTION"));
  }

  public static boolean canItemBeUsed(Player player, @Nullable ItemStack itemStack) {
    Material type = itemStack == null ? Material.AIR : itemStack.getType();
    if (/*ITEM_TRIDENT != null && */type == ITEM_TRIDENT) {
      User user = UserRepository.userOf(player);
      return tridentUsable(user, itemStack);
    }

    // Bow check
    boolean hasArrows = inventoryContains(player, arrowItems);
    if (type == Material.BOW && !hasArrows) {
      return false;
    }
    if (type == CROSSBOW && !hasArrows) {
      return false;
    }

    boolean useItem = materialUseItems.contains(type);
    boolean potion = materialPotionItems.contains(type);
    boolean sword = materialSwordItems.contains(type) && swordBlockable(player, type);
    return sword || useItem || potion || foodConsumable(player, type);
  }

  public static boolean isBow(Material type) {
    return type == Material.BOW;
  }

  public static boolean isPotion(Material type) {
    return materialPotionItems.contains(type);
  }

  public static boolean swordBlockable(Player player, Material material) {
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    return !protocol.combatUpdate() || protocol.viaVersionShieldBlockReplacement();
  }

  public static boolean foodConsumable(Player player, Material type) {
    User user = UserRepository.userOf(player);
    AbilityMetadata abilityData = user.meta().abilities();
    boolean creative = abilityData.inGameMode(AbilityTracker.GameMode.CREATIVE);
    if (creative) {
      return false;
    }
    if (foodLevelConstraintFoodItems.contains(type)) {
      return user.player().getFoodLevel() < 20;
    }
    return nonFoodLevelConstraintFoodItems.contains(type);
  }

  public static boolean isSwordItem(@Nullable ItemStack itemStack) {
    Material type = itemStack == null ? Material.AIR : itemStack.getType();
    return materialSwordItems.contains(type);
  }

  public static boolean isTrapdoor(Material material) {
    return materialTrapdoorItems.contains(material);
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
    for (Material materiall : Material.values()) {
      if (materiall.name().equalsIgnoreCase(name)) {
        return materiall;
      }
    }
    return null;
  }

  private static boolean inventoryContains(Player player, Collection<Material> items) {
    PlayerInventory inventory = player.getInventory();
    for (ItemStack content : inventory.getContents()) {
      if (content != null && items.contains(content.getType())) {
        return true;
      }
    }
    return false;
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