package de.jpx3.intave.module.test;

import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ChestLootProvider extends Module {
  private final Map<Player, Location> lootChests = GarbageCollector.watch(new WeakHashMap<>());

  @Override
  public void disable() {
    for (Location location : lootChests.values()) {
      location.getBlock().setType(Material.REDSTONE_ORE);
    }
  }

  @BukkitEventSubscription
  public void on(PlayerQuitEvent quit) {
    Location remove = lootChests.remove(quit.getPlayer());
    if (remove != null) {
      remove.getBlock().setType(Material.REDSTONE_ORE);
    }
  }

  @BukkitEventSubscription
  public void on(PlayerInteractEvent interact) {
    if (interact.getClickedBlock() == null) {
      return;
    }
    Location location = interact.getClickedBlock().getLocation().clone();
    location.setYaw(0);
    location.setPitch(0);
    location.setX(floor(location.getX()));
    location.setY(floor(location.getY()));
    location.setZ(floor(location.getZ()));

    if (lootChests.containsValue(location)) {
      // check player
      if (interact.getPlayer() == null) {
        return;
      }
      Player player = interact.getPlayer();
      if (!lootChests.containsKey(player)) {
        return;
      }
      // fill chest
      Chest chest = (Chest) interact.getClickedBlock().getState();
      fillChest(chest);
    }
  }

  @BukkitEventSubscription
  public void on(InventoryCloseEvent close) {
    if (!(close.getPlayer() instanceof Player)) {
      return;
    }
    Player player = (Player) close.getPlayer();
    if (lootChests.containsKey(player)) {
      Synchronizer.synchronizeDelayed(() -> {
        // clear player inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
      }, 20);
    }
  }

  public void openLootChestCommand(Player player) {
    // if player already has a loot chest, remove it
    if (lootChests.containsKey(player)) {
      Location remove = lootChests.remove(player);
      if (remove != null) {
        remove.getBlock().setType(Material.REDSTONE_ORE);
      }
      return;
    }

    // create a new loot chest
    Location location = player.getLocation().clone().add(0, -1, 0);
    location.setYaw(0);
    location.setPitch(0);
    location.setX(floor(location.getX()));
    location.setY(floor(location.getY()));
    location.setZ(floor(location.getZ()));
    location.getBlock().setType(Material.CHEST);
    lootChests.put(player, location);

    // fill the chest with loot
    Chest leChest = (Chest) location.getBlock().getState();
    fillChest(leChest);
  }

  private static int floor(double value) {
    int i = (int) value;
    return value < (double) i ? i - 1 : i;
  }

  private Set<Material> lootTable() {
    return MaterialSearch.materialsThatContain(
      "HELMET",
      "CHESTPLATE",
      "LEGGINGS",
      "BOOTS",
      "SWORD",
      "AXE",
      "PICKAXE",
      "SHOVEL",
      "HOE",
      "DIRT",
      "STONE",
      "WOOD",
      "LOG"
    );
  }

  private void fillChest(Chest chest) {
    Inventory inventory = chest.getBlockInventory();
    inventory.clear();

    List<Material> lootTable = new ArrayList<>(lootTable());
    Set<Material> selectedLoot = new HashSet<>(); // select 7 random loot items

    Random random = new Random();
    while (selectedLoot.size() < 18) {
      Material randomMaterial = lootTable.get(random.nextInt(lootTable.size()));
      selectedLoot.add(randomMaterial);
    }

    for (Material material : selectedLoot) {
      int randomIndex;
      do {
        randomIndex = random.nextInt(inventory.getSize());
      } while (inventory.getItem(randomIndex) != null);
      inventory.setItem(randomIndex, new ItemStack(material, material.isBlock() ? ThreadLocalRandom.current().nextInt(1, 64) : 1));
    }
  }
}
