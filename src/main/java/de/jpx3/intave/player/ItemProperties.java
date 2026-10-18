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

package de.jpx3.intave.player;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

import static de.jpx3.intave.player.Enchantments.tridentRiptideEnchanted;

public final class ItemProperties {
  private static final ItemUseProfile DEFAULT_USE_PROFILE = new ItemUseProfile(false, 0.2F);
  public static final Material ITEM_TRIDENT = materialByName("TRIDENT");
  public static final Material CROSSBOW = materialByName("CROSSBOW");
  private static final Set<Material> materialSwordItems = EnumSet.noneOf(Material.class);
  private static final Set<Material> materialTrapdoorItems = EnumSet.noneOf(Material.class);
  private static final Set<Material> foodLevelConstraintFoodItems = EnumSet.noneOf(Material.class);
  private static final Set<Material> nonFoodLevelConstraintFoodItems = EnumSet.noneOf(Material.class);
  private static final Set<Material> arrowItems = EnumSet.noneOf(Material.class);

  public static void setup() {
    try {
      loadDefaultUseItems();
      loadFoodItems();
      loadArrows();
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static void loadFoodItems() {
    List<String> foodLevelConstraintFoodItemNames = Arrays.asList(
      "apple", "bread", "porkchop", "cooked_porkchop",
      "pork", "grilled_pork", "cookie", "melon", "beef", "raw_beef",
      "cooked_beef", "chicken", "cooked_chicken", "rotten_flesh",
      "spider_eye", "baked_potato", "poisonous_potato", "golden_carrot",
      "pumpkin_pie", "rabbit", "cooked_rabbit", "mutton", "cooked_mutton",
      "mushroom_soup", "raw_fish", "cooked_fish", "raw_chicken",
      "carrot_item", "potato_item", "rabbit_stew", "beetroot", "beetroot_soup",
      "carrot", "cod", "cooked_cod", "cooked_salmon", "dried_kelp", "glow_berries",
      "melon_slice", "mushroom_stew", "potato", "pufferfish", "salmon", "sweet_berries",
      "tropical_fish"
    );
    List<String> nonFoodLevelConstraintFoodItemNames =
      Arrays.asList("golden_apple", "enchanted_golden_apple", "chorus_fruit");
    materialListConvert(foodLevelConstraintFoodItemNames, foodLevelConstraintFoodItems);
    materialListConvert(nonFoodLevelConstraintFoodItemNames, nonFoodLevelConstraintFoodItems);
  }

  private static void materialListConvert(List<String> input, Set<? super Material> output) {
    input.stream().map(ItemProperties::materialByName).filter(Objects::nonNull).forEach(output::add);
  }

  private static void loadArrows() {
    materialListConvert(Arrays.asList("arrow", "spectral_arrow", "tipped_arrow"), arrowItems);
  }

  private static void loadDefaultUseItems() {
    materialTrapdoorItems.addAll(materialsMatching("TRAP_DOOR"));
    materialTrapdoorItems.addAll(materialsMatching("TRAPDOOR"));
    materialSwordItems.addAll(materialsMatching("SWORD"));
  }

  public static boolean canItemBeUsed(User user, @Nullable ItemStack itemStack) {
    return itemUseProfile(user, itemStack) != null;
  }

  public static boolean startsUsingOnBlock(
    User user, @Nullable ItemStack itemStack
  ) {
    return itemStack != null
      && itemStack.getType().name().equals("BRUSH")
      && user.meta().protocol().protocolVersion() >= ProtocolMetadata.VER_1_19_4;
  }

  @Nullable
  public static ItemUseProfile itemUseProfile(User user, @Nullable ItemStack itemStack) {
    if (!isUsableItem(user, itemStack)) {
      return null;
    }
    if (user.meta().protocol().protocolVersion() < ProtocolMetadata.VER_1_21_11) {
      return DEFAULT_USE_PROFILE;
    }
    return componentUseProfile(itemStack);
  }

  private static boolean isUsableItem(User user, @Nullable ItemStack itemStack) {
    Material type = itemStack == null ? Material.AIR : itemStack.getType();
    int protocolVersion = user.meta().protocol().protocolVersion();
    if (/*ITEM_TRIDENT != null && */type == ITEM_TRIDENT) {
      return protocolVersion >= ProtocolMetadata.VER_1_13 && tridentUsable(user, itemStack);
    }

    if (type == Material.BOW) {
      return projectileAvailable(user, false);
    }
    if (type == CROSSBOW) {
      return canChargeCrossbow(user, itemStack);
    }
    boolean useItem = isDefaultUseItem(type, protocolVersion);
    boolean potion = drinkablePotion(itemStack);
    boolean sword = materialSwordItems.contains(type) && swordBlockable(user.player());
    boolean kineticWeapon = itemStack != null
      && protocolVersion >= ProtocolMetadata.VER_1_21_11
      && itemMetaComponent(itemStack, "hasKineticWeapon", "getKineticWeapon") != null;
    return sword || useItem || potion || kineticWeapon || consumableItem(user, itemStack);
  }

  public static boolean canChargeCrossbow(User user, @Nullable ItemStack itemStack) {
    if (user.meta().protocol().protocolVersion() < ProtocolMetadata.VER_1_14
      || itemStack == null || itemStack.getType() != CROSSBOW) {
      return false;
    }
    return crossbowCanStartUsing(crossbowLoaded(itemStack), projectileAvailable(user, true));
  }

  static boolean crossbowCanStartUsing(boolean charged, boolean projectileAvailable) {
    return !charged && projectileAvailable;
  }

  public static boolean crossbowLoaded(ItemStack itemStack) {
    if (itemStack == null) {
      return false;
    }
    if (itemStack.getType() != CROSSBOW) {
      return false;
    }
    ItemMeta meta = itemStack.getItemMeta();
    if (meta == null) {
      return false;
    }
    Class<?> clazz = meta.getClass();
    try {
      return (boolean) clazz.getMethod("hasChargedProjectiles").invoke(meta);
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean isBow(Material type) {
    return type == Material.BOW;
  }

  public static boolean isPotion(Material type) {
    return type == Material.POTION;
  }

  public static boolean swordBlockable(Player player) {
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    return protocol.swordBlockingPossible() || protocol.viaVersionShieldBlockReplacement();
  }

  public static boolean foodConsumable(User user, Material type) {
    return isFood(type) && foodAllowed(user, alwaysEdible(type, user.meta().protocol().protocolVersion()));
  }

  public static boolean foodConsumable(User user, @Nullable ItemStack itemStack) {
    if (itemStack == null) {
      return false;
    }
    if (!hasItemMeta(itemStack)) {
      return foodConsumable(user, itemStack.getType());
    }
    int protocolVersion = user.meta().protocol().protocolVersion();
    Object foodComponent = protocolVersion >= ProtocolMetadata.VER_1_20_5
      ? itemMetaComponent(itemStack, "hasFood", "getFood") : null;
    if (foodComponent != null) {
      return componentUsePossible(user, itemStack, foodComponent);
    }
    return foodConsumable(user, itemStack.getType());
  }

  private static boolean foodAllowed(User user, boolean alwaysEdible) {
    AbilityMetadata abilityData = user.meta().abilities();
    boolean creative = abilityData.inGameMode(AbilityTracker.GameMode.CREATIVE);
    int protocolVersion = user.meta().protocol().protocolVersion();
    if (creative && protocolVersion < ProtocolMetadata.VER_1_15) {
      return false;
    }
    if (alwaysEdible || creative) {
      return true;
    }
    Player player = user.player();
    return player != null && player.getFoodLevel() < 20;
  }

  static boolean alwaysEdible(Material type, int protocolVersion) {
    return nonFoodLevelConstraintFoodItems.contains(type)
      || type != null && type.name().equals("SUSPICIOUS_STEW")
      && protocolVersion >= ProtocolMetadata.VER_1_17;
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
    SimulationEnvironment movementData = user.meta().movement();
    if (tridentRiptideEnchanted(itemStack)) {
      return movementData.inWater() || (world.isThundering() || world.hasStorm());
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
    if (player == null) {
      return false;
    }
    PlayerInventory inventory = player.getInventory();
    for (ItemStack content : inventory.getContents()) {
      if (content != null && items.contains(content.getType())) {
        return true;
      }
    }
    return false;
  }

  private static boolean projectileAvailable(User user, boolean crossbow) {
    if (user.meta().abilities().inGameMode(AbilityTracker.GameMode.CREATIVE)) {
      return true;
    }
    Player player = user.player();
    if (player == null) {
      return false;
    }
    if (crossbow) {
      PlayerInventory inventory = player.getInventory();
      if (crossbowHeldProjectile(inventory.getItemInMainHand())
        || crossbowHeldProjectile(inventory.getItemInOffHand())) {
        return true;
      }
    }
    return inventoryContains(player, arrowItems);
  }

  private static boolean crossbowHeldProjectile(@Nullable ItemStack itemStack) {
    return itemStack != null && crossbowProjectile(itemStack.getType(), true);
  }

  static boolean crossbowProjectile(Material type, boolean held) {
    return type != null && (arrowItems.contains(type)
      || held && type.name().equals("FIREWORK_ROCKET"));
  }

  static boolean isDefaultUseItem(Material type, int protocolVersion) {
    if (type == null) {
      return false;
    }
    String name = type.name();
    if (name.equals("BUNDLE") || name.endsWith("_BUNDLE")) {
      return protocolVersion >= ProtocolMetadata.VER_1_21_2;
    }
    switch (name) {
      case "MILK_BUCKET":
        return protocolVersion >= ProtocolMetadata.VER_1_8;
      case "SHIELD":
        return protocolVersion >= ProtocolMetadata.VER_1_9;
      case "HONEY_BOTTLE":
        return protocolVersion >= ProtocolMetadata.VER_1_15;
      case "SPYGLASS":
        return protocolVersion >= ProtocolMetadata.VER_1_17;
      case "GOAT_HORN":
        return protocolVersion >= ProtocolMetadata.VER_1_19;
      case "BRUSH":
        return protocolVersion >= ProtocolMetadata.VER_1_19_4;
      case "OMINOUS_BOTTLE":
        return protocolVersion >= ProtocolMetadata.VER_1_21;
      default:
        return name.endsWith("_SPEAR")
          && protocolVersion >= ProtocolMetadata.VER_1_21_11;
    }
  }

  static boolean isFood(Material type) {
    return type != null && (foodLevelConstraintFoodItems.contains(type)
      || nonFoodLevelConstraintFoodItems.contains(type));
  }

  static boolean drinkablePotion(@Nullable ItemStack itemStack) {
    if (itemStack == null || itemStack.getType() != Material.POTION) {
      return false;
    }
    return drinkablePotion(itemStack.getType(), itemStack.getDurability());
  }

  static boolean drinkablePotion(Material type, int durability) {
    return type == Material.POTION && (durability & 0x4000) == 0;
  }

  private static boolean consumableItem(User user, @Nullable ItemStack itemStack) {
    if (itemStack == null) {
      return false;
    }
    if (!hasItemMeta(itemStack)) {
      return foodConsumable(user, itemStack.getType());
    }
    int protocolVersion = user.meta().protocol().protocolVersion();
    Object foodComponent = protocolVersion >= ProtocolMetadata.VER_1_20_5
      ? itemMetaComponent(itemStack, "hasFood", "getFood") : null;
    if (foodComponent != null) {
      return componentUsePossible(user, itemStack, foodComponent);
    }
    return foodConsumable(user, itemStack.getType())
      || protocolVersion >= ProtocolMetadata.VER_1_21_2
      && componentUsePossible(user, itemStack, null);
  }

  private static boolean componentUsePossible(
    User user, ItemStack itemStack, @Nullable Object foodComponent
  ) {
    int protocolVersion = user.meta().protocol().protocolVersion();
    if (protocolVersion < ProtocolMetadata.VER_1_20_5) {
      return false;
    }
    Number useSeconds;
    if (protocolVersion < ProtocolMetadata.VER_1_21_2) {
      useSeconds = componentValue(
        foodComponent,
        "org.bukkit.inventory.meta.components.FoodComponent",
        "getEatSeconds",
        Number.class
      );
      if (useSeconds == null) {
        useSeconds = consumableDuration(itemStack);
      }
    } else {
      useSeconds = consumableDuration(itemStack);
      if (useSeconds == null && foodComponent != null) {
        useSeconds = componentValue(
          foodComponent,
          "org.bukkit.inventory.meta.components.FoodComponent",
          "getEatSeconds",
          Number.class
        );
      }
    }
    if (useSeconds == null || useSeconds.floatValue() <= 0.0F) {
      return false;
    }
    if (foodComponent == null) {
      return true;
    }
    Boolean alwaysEdible = componentValue(
      foodComponent,
      "org.bukkit.inventory.meta.components.FoodComponent",
      "canAlwaysEat",
      Boolean.class
    );
    return alwaysEdible != null && foodAllowed(user, alwaysEdible);
  }

  @Nullable
  private static Number consumableDuration(ItemStack itemStack) {
    Object consumable = itemMetaComponent(itemStack, "hasConsumable", "getConsumable");
    return componentValue(
      consumable,
      "org.bukkit.inventory.meta.components.consumable.ConsumableComponent",
      "getConsumeSeconds",
      Number.class
    );
  }

  private static ItemUseProfile componentUseProfile(@Nullable ItemStack itemStack) {
    if (itemStack == null) {
      return DEFAULT_USE_PROFILE;
    }
    Object useEffects = itemMetaComponent(itemStack, "hasUseEffects", "getUseEffects");
    if (useEffects == null) {
      return DEFAULT_USE_PROFILE;
    }
    Boolean canSprint = componentValue(
      useEffects,
      "org.bukkit.inventory.meta.components.UseEffectsComponent",
      "canSprint",
      Boolean.class
    );
    Number speedMultiplier = componentValue(
      useEffects,
      "org.bukkit.inventory.meta.components.UseEffectsComponent",
      "getSpeedMultiplier",
      Number.class
    );
    if (canSprint == null || speedMultiplier == null) {
      return DEFAULT_USE_PROFILE;
    }
    float multiplier = speedMultiplier.floatValue();
    if (!Float.isFinite(multiplier) || multiplier < 0.0F || multiplier > 1.0F) {
      return DEFAULT_USE_PROFILE;
    }
    return new ItemUseProfile(canSprint, multiplier);
  }

  @Nullable
  private static Object itemMetaComponent(ItemStack itemStack, String hasMethod, String getMethod) {
    try {
      ItemMeta meta = itemStack.getItemMeta();
      if (meta == null || !(boolean) ItemMeta.class.getMethod(hasMethod).invoke(meta)) {
        return null;
      }
      return ItemMeta.class.getMethod(getMethod).invoke(meta);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
      return null;
    }
  }

  private static boolean hasItemMeta(ItemStack itemStack) {
    try {
      return itemStack.hasItemMeta();
    } catch (RuntimeException | LinkageError ignored) {
      return false;
    }
  }

  @Nullable
  private static <T> T componentValue(
    @Nullable Object component, String componentType, String methodName, Class<T> returnType
  ) {
    if (component == null) {
      return null;
    }
    try {
      Class<?> type = Class.forName(componentType);
      Object value = type.getMethod(methodName).invoke(component);
      return returnType.isInstance(value) ? returnType.cast(value) : null;
    } catch (ReflectiveOperationException | LinkageError ignored) {
      return null;
    }
  }

  public static final class ItemUseProfile {
    private final boolean canSprint;
    private final float speedMultiplier;

    private ItemUseProfile(boolean canSprint, float speedMultiplier) {
      this.canSprint = canSprint;
      this.speedMultiplier = speedMultiplier;
    }

    public boolean canSprint() {
      return canSprint;
    }

    public float speedMultiplier() {
      return speedMultiplier;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof ItemUseProfile)) {
        return false;
      }
      ItemUseProfile that = (ItemUseProfile) object;
      return canSprint == that.canSprint
        && Float.compare(speedMultiplier, that.speedMultiplier) == 0;
    }

    @Override
    public int hashCode() {
      return Objects.hash(canSprint, speedMultiplier);
    }
  }
}
