package de.jpx3.intave.player.fake.equipment;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class EquipmentFactory {
  private final static Map<ArmorSlot, List<Material>> armorSets = Maps.newHashMap();
  private final static List<Material> heldItems;

  static {
    heldItems = ImmutableList.of(
      // tools
      Material.WOOD_SWORD,
      Material.GOLD_SWORD,
      Material.STONE_SWORD,
      Material.IRON_SWORD,
      Material.DIAMOND_SWORD,
      Material.WOOD_AXE,
      Material.GOLD_AXE,
      Material.STONE_AXE,
      Material.IRON_AXE,
      Material.DIAMOND_AXE,
      Material.WOOD_PICKAXE,
      Material.GOLD_PICKAXE,
      Material.STONE_PICKAXE,
      Material.IRON_PICKAXE,
      Material.DIAMOND_PICKAXE,

      Material.AIR,
      Material.AIR,
      Material.AIR
    );
    armorSets.put(ArmorSlot.HELMET, ImmutableList.of(
      Material.LEATHER_HELMET,
      Material.CHAINMAIL_HELMET,
      Material.IRON_HELMET,
      Material.DIAMOND_HELMET,
      Material.AIR,
      Material.AIR
    ));
    armorSets.put(ArmorSlot.CHESTPLATE, ImmutableList.of(
      Material.LEATHER_CHESTPLATE,
      Material.CHAINMAIL_CHESTPLATE,
      Material.IRON_CHESTPLATE,
      Material.DIAMOND_CHESTPLATE,
      Material.AIR,
      Material.AIR
    ));
    armorSets.put(ArmorSlot.LEGGINGS, ImmutableList.of(
      Material.LEATHER_LEGGINGS,
      Material.CHAINMAIL_LEGGINGS,
      Material.IRON_LEGGINGS,
      Material.DIAMOND_LEGGINGS,
      Material.AIR,
      Material.AIR
    ));
    armorSets.put(ArmorSlot.BOOTS, ImmutableList.of(
      Material.LEATHER_BOOTS,
      Material.CHAINMAIL_BOOTS,
      Material.IRON_BOOTS,
      Material.DIAMOND_BOOTS,
      Material.AIR,
      Material.AIR
    ));
  }

  public static Equipment randomEquipment() {
    List<ArmorPiece> armorPieceList = Lists.newArrayList();
    Arrays.stream(ArmorSlot.values())
      .map(EquipmentFactory::armorPieceForSlot)
      .forEach(armorPieceList::add);
    return new Equipment(armorPieceList, randomHandItem());
  }

  private static ArmorPiece armorPieceForSlot(
    ArmorSlot armorSlot
  ) {
    List<Material> materials = armorSets.get(armorSlot);
    int materialSlot = ThreadLocalRandom.current().nextInt(0, materials.size());
    return new ArmorPiece(armorSlot, materials.get(materialSlot));
  }

  private static Material randomHandItem() {
    int id = ThreadLocalRandom.current().nextInt(0, heldItems.size());
    return heldItems.get(id);
  }
}