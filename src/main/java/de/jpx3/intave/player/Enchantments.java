package de.jpx3.intave.player;

import de.jpx3.intave.executor.BackgroundExecutors;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class Enchantments {
  public static final Enchantment ENCHANTMENT_RIPTIDE;
	private static final Enchantment ENCHANTMENT_SWIFT_SNEAK;
	private static final Enchantment ENCHANTMENT_SOUL_SPEED;
  private static final Enchantment ENCHANTMENT_DEPTH_STRIDER;

  static {
    Enchantment riptide = null;
    Enchantment swiftSneak = null;
    Enchantment soulSpeed = null;
    Enchantment depthStrider = null;
    try {
      riptide = Enchantment.getByName("RIPTIDE");
      swiftSneak = Enchantment.getByName("SWIFT_SNEAK");
      soulSpeed = Enchantment.getByName("SOUL_SPEED");
      depthStrider = Enchantment.getByName("DEPTH_STRIDER");
    } catch (Throwable ignored) {}
    ENCHANTMENT_RIPTIDE = riptide;
    ENCHANTMENT_SWIFT_SNEAK = swiftSneak;
    ENCHANTMENT_SOUL_SPEED = soulSpeed;
    ENCHANTMENT_DEPTH_STRIDER = depthStrider;
  }

  public static boolean tridentRiptideEnchanted(ItemStack itemStack) {
    return itemStack.getEnchantments().containsKey(ENCHANTMENT_RIPTIDE);
  }

  public static float resolveDepthStriderModifier(Player player) {
    if (ENCHANTMENT_DEPTH_STRIDER == null) {
      return 0;
    }
    return resolveEnchantmentLevel(
      ENCHANTMENT_DEPTH_STRIDER, player.getInventory().getArmorContents());
  }

  public static int resolveSoulSpeedModifier(Player player) {
    ItemStack boots = player.getInventory().getBoots();
    if (ENCHANTMENT_SOUL_SPEED == null || boots == null) {
      return 0;
    }
    return resolveEnchantmentLevel(ENCHANTMENT_SOUL_SPEED, boots);
  }

  public static int resolveSwiftSpeedModifier(Player player) {
    ItemStack leggings = player.getInventory().getLeggings();
    if (ENCHANTMENT_SWIFT_SNEAK == null || leggings == null) {
      return 0;
    }
    return resolveEnchantmentLevel(ENCHANTMENT_SWIFT_SNEAK, leggings);
  }

  public static int resolveRiptideModifier(ItemStack stack) {
    if (ENCHANTMENT_RIPTIDE == null || stack == null) {
      return 0;
    }
    return resolveEnchantmentLevel(ENCHANTMENT_RIPTIDE, stack);
  }

  private static Map<String, Set<Material>> SUPPORTED_TYPES = new HashMap<>();

  private static int resolveEnchantmentLevel(Enchantment enchantment, ItemStack itemStack) {
    if (itemStack == null) {
      return 0;
    }
    Set<Material> supportedTypes = SUPPORTED_TYPES.get(enchantment.getName());
    if (supportedTypes != null && !supportedTypes.contains(itemStack.getType())) {
      return 0;
    }
    if (supportedTypes == null) {
      SUPPORTED_TYPES.put(enchantment.getName(), EnumSet.allOf(Material.class));
      BackgroundExecutors.execute(() -> {
        Set<Material> figureOutEnchantsLaterHere = EnumSet.noneOf(Material.class);
        for (Material value : Material.values()) {
          if (!value.isBlock()) {
            if (enchantment.canEnchantItem(new ItemStack(value))) {
              figureOutEnchantsLaterHere.add(value);
            }
          }
        }
        SUPPORTED_TYPES.put(enchantment.getName(), figureOutEnchantsLaterHere);
      });
    }
    return itemStack.getEnchantmentLevel(enchantment);
  }

  private static int resolveEnchantmentLevel(Enchantment enchantment, ItemStack[] stacks) {
    if (stacks == null || stacks.length == 0) {
      return 0;
    }
    int enchantmentLevel = 0;
    for (ItemStack itemstack : stacks) {
      if (itemstack == null) {
        continue;
      }
      int level = itemstack.getEnchantmentLevel(enchantment);
      if (level > enchantmentLevel) {
        enchantmentLevel = level;
      }
    }
    return enchantmentLevel;
  }
}
