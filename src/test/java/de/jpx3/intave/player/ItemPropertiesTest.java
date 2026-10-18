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

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.inventory.meta.components.UseEffectsComponent;
import org.bukkit.inventory.meta.components.consumable.ConsumableComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPropertiesTest {

  @BeforeAll
  static void setUpItemProperties() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
    ItemProperties.setup();
  }

  @Test
  void crossbowOnlyStartsUsingWhileUnchargedWithAProjectile() {
    assertTrue(ItemProperties.crossbowCanStartUsing(false, true));
    assertFalse(ItemProperties.crossbowCanStartUsing(false, false));
    assertFalse(ItemProperties.crossbowCanStartUsing(true, true));
    assertFalse(ItemProperties.crossbowCanStartUsing(true, false));
  }

  @Test
  void readsTheChargedCrossbowStateFromItemMetadata() {
    assertTrue(ItemProperties.crossbowLoaded(crossbowWithChargedState(true)));
    assertFalse(ItemProperties.crossbowLoaded(crossbowWithChargedState(false)));
  }

  @Test
  void crossbowAcceptsHeldFireworksButOnlyArrowsFromInventory() {
    assertTrue(ItemProperties.crossbowProjectile(Material.ARROW, false));
    assertTrue(ItemProperties.crossbowProjectile(Material.TIPPED_ARROW, false));
    assertTrue(ItemProperties.crossbowProjectile(Material.SPECTRAL_ARROW, false));
    assertTrue(ItemProperties.crossbowProjectile(Material.FIREWORK_ROCKET, true));
    assertFalse(ItemProperties.crossbowProjectile(Material.FIREWORK_ROCKET, false));

    Material potterySherd = Material.getMaterial("ARROW_POTTERY_SHERD");
    if (potterySherd != null) {
      assertFalse(ItemProperties.crossbowProjectile(potterySherd, true));
    }
  }

  @Test
  void sustainedUseMaterialsRespectTheirClientIntroduction() {
    assertAvailableSince("MILK_BUCKET", ProtocolMetadata.VER_1_8);
    assertAvailableSince("SHIELD", ProtocolMetadata.VER_1_9);
    assertAvailableSince("HONEY_BOTTLE", ProtocolMetadata.VER_1_15);
    assertAvailableSince("SPYGLASS", ProtocolMetadata.VER_1_17);
    assertAvailableSince("GOAT_HORN", ProtocolMetadata.VER_1_19);
    assertAvailableSince("BRUSH", ProtocolMetadata.VER_1_19_4);
    assertAvailableSince("OMINOUS_BOTTLE", ProtocolMetadata.VER_1_21);
    assertAvailableSince("BUNDLE", ProtocolMetadata.VER_1_21_2);
    assertAvailableSince("WOODEN_SPEAR", ProtocolMetadata.VER_1_21_11);
  }

  @Test
  void instantUseItemsAreNotClassifiedAsSustainedUseItems() {
    assertFalse(ItemProperties.isDefaultUseItem(Material.SPLASH_POTION, ProtocolMetadata.VER_26_3));
    assertFalse(ItemProperties.isDefaultUseItem(Material.LINGERING_POTION, ProtocolMetadata.VER_26_3));
    assertFalse(ItemProperties.isDefaultUseItem(Material.ENDER_EYE, ProtocolMetadata.VER_26_3));
  }

  @Test
  void foodClassificationIncludesFoodsAddedAfterTheLegacyList() {
    assertTrue(ItemProperties.isFood(Material.COOKED_BEEF));
    assertTrue(ItemProperties.isFood(Material.SWEET_BERRIES));
    assertFalse(ItemProperties.isFood(Material.STICK));
  }

  @Test
  void recognizesComponentConsumablesWithTheirUseEffectsProfile() {
    User hungry = userWithFoodLevel(10);
    User full = userWithFoodLevel(20);

    assertTrue(ItemProperties.canItemBeUsed(hungry, componentItem(true, 1.6F, false, 0.2F)));
    assertFalse(ItemProperties.canItemBeUsed(full, componentItem(true, 1.6F, false, 0.2F)));
    assertTrue(ItemProperties.canItemBeUsed(full, componentItem(false, 1.6F, false, 0.2F)));
    assertFalse(ItemProperties.canItemBeUsed(hungry, componentItem(false, 0.0F, false, 0.2F)));

    ItemProperties.ItemUseProfile sprintingProfile = ItemProperties.itemUseProfile(
      hungry, componentItem(false, 1.6F, true, 0.5F)
    );
    assertNotNull(sprintingProfile);
    assertTrue(sprintingProfile.canSprint());
    assertEquals(0.5F, sprintingProfile.speedMultiplier());
  }

  @Test
  void activeUseCapturesTheExact12111UseEffectsProfile() {
    User user = userWithFoodLevel(10);
    ItemStack item = componentItem(false, 1.6F, true, 0.5F);
    user.player().getInventory().setItem(0, item);
    user.meta().inventory().setHeldItemSlot(0);

    assertTrue(user.meta().inventory().activateHandIfUsable(null));
    assertTrue(user.meta().inventory().activeItemAllowsSprinting());
    assertEquals(0.5F, user.meta().inventory().activeItemUseSpeedMultiplier());
  }

  @Test
  void suspiciousStewBecomesAlwaysEdibleIn117() {
    assertFalse(ItemProperties.alwaysEdible(Material.SUSPICIOUS_STEW, ProtocolMetadata.VER_1_16_4));
    assertTrue(ItemProperties.alwaysEdible(Material.SUSPICIOUS_STEW, ProtocolMetadata.VER_1_17));
    assertTrue(ItemProperties.alwaysEdible(Material.GOLDEN_APPLE, ProtocolMetadata.VER_1_8));
    assertTrue(ItemProperties.alwaysEdible(Material.CHORUS_FRUIT, ProtocolMetadata.VER_1_9));
  }

  @Test
  void legacySplashPotionIsNotASustainedUseItem() {
    assertTrue(ItemProperties.drinkablePotion(Material.POTION, 0));
    assertFalse(ItemProperties.drinkablePotion(Material.POTION, 0x4000));
    assertFalse(ItemProperties.drinkablePotion(Material.SPLASH_POTION, 0));
  }

  private static void assertAvailableSince(String materialName, int minimumProtocol) {
    Material material = Material.getMaterial(materialName);
    assertNotNull(material, materialName);
    assertFalse(ItemProperties.isDefaultUseItem(material, minimumProtocol - 1), materialName);
    assertTrue(ItemProperties.isDefaultUseItem(material, minimumProtocol), materialName);
  }

  private static ItemStack crossbowWithChargedState(boolean charged) {
    CrossbowMeta meta = (CrossbowMeta) Proxy.newProxyInstance(
      CrossbowMeta.class.getClassLoader(),
      new Class<?>[]{CrossbowMeta.class},
      (proxy, method, arguments) -> method.getName().equals("hasChargedProjectiles") && charged
    );
    return new ItemStack(Material.CROSSBOW) {
      @Override
      public ItemMeta getItemMeta() {
        return meta;
      }
    };
  }

  private static ItemStack componentItem(
    boolean food, float consumeSeconds, boolean canSprint, float speedMultiplier
  ) {
    FoodComponent foodComponent = proxy(FoodComponent.class, values(
      "canAlwaysEat", false
    ));
    ConsumableComponent consumable = proxy(ConsumableComponent.class, values(
      "getConsumeSeconds", consumeSeconds
    ));
    UseEffectsComponent useEffects = proxy(UseEffectsComponent.class, values(
      "canSprint", canSprint,
      "getSpeedMultiplier", speedMultiplier
    ));
    ItemMeta meta = proxy(ItemMeta.class, values(
      "hasFood", food,
      "getFood", foodComponent,
      "hasConsumable", true,
      "getConsumable", consumable,
      "hasUseEffects", true,
      "getUseEffects", useEffects,
      "hasKineticWeapon", false
    ));
    return new ItemStack(Material.STICK) {
      @Override
      public boolean hasItemMeta() {
        return true;
      }

      @Override
      public ItemMeta getItemMeta() {
        return meta;
      }
    };
  }

  private static User userWithFoodLevel(int foodLevel) {
    return UserFactory.createTestUserFor(
      FakePlayerFactory.createPlayer((methodName, arguments) -> {
        if (methodName.equals("getFoodLevel")) {
          return foodLevel;
        }
        if (methodName.equals("getLocation")) {
          return new Location(null, 0.0D, 0.0D, 0.0D);
        }
        return null;
      }),
      ProtocolMetadata.VER_26_3
    );
  }

  private static Map<String, Object> values(Object... entries) {
    Map<String, Object> values = new HashMap<>();
    for (int index = 0; index < entries.length; index += 2) {
      values.put((String) entries[index], entries[index + 1]);
    }
    return values;
  }

  private static <T> T proxy(Class<T> type, Map<String, Object> values) {
    Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
      (instance, method, arguments) -> values.get(method.getName()));
    return type.cast(proxy);
  }
}
