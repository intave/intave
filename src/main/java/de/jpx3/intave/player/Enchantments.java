package de.jpx3.intave.player;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class Enchantments {
  private final static Enchantment ENCHANTMENT_SOUL_SPEED = Enchantment.getByName("SOUL_SPEED");
  private final static Enchantment ENCHANTMENT_DEPTH_STRIDER = Enchantment.getByName("DEPTH_STRIDER");
  public final static Enchantment ENCHANTMENT_RIPTIDE = Enchantment.getByName("RIPTIDE");

  public static boolean tridentRiptideEnchanted(ItemStack itemStack) {
    return itemStack.getEnchantments().containsKey(ENCHANTMENT_RIPTIDE);
  }

  public static float resolveDepthStriderModifier(Player player) {
    if (ENCHANTMENT_DEPTH_STRIDER == null) {
      return 0;
    }
    return resolveEnchantmentLevel(ENCHANTMENT_DEPTH_STRIDER, player.getInventory().getArmorContents());
  }

  public static int resolveSoulSpeedModifier(Player player) {
    ItemStack boots = player.getInventory().getBoots();
    if (ENCHANTMENT_SOUL_SPEED == null || boots == null) {
      return 0;
    }
    return resolveEnchantmentLevel(ENCHANTMENT_SOUL_SPEED, boots);
  }

  public static int resolveRiptideModifier(ItemStack stack) {
    if (ENCHANTMENT_RIPTIDE == null) {
      return 0;
    }
    return resolveEnchantmentLevel(ENCHANTMENT_RIPTIDE, stack);
  }

  private static int resolveEnchantmentLevel(Enchantment enchantment, ItemStack itemStack) {
    return itemStack.getEnchantmentLevel(enchantment);
  }

  private static int resolveEnchantmentLevel(Enchantment enchantment, ItemStack[] stacks) {
    if (stacks == null || stacks.length == 0) {
      return 0;
    }
    int depthStriderLevel = 0;
    for (ItemStack itemstack : stacks) {
      if (itemstack == null) {
        continue;
      }
      int level = itemstack.getEnchantmentLevel(enchantment);
      if (level > depthStriderLevel) {
        depthStriderLevel = level;
      }
    }
    return depthStriderLevel;
  }
}