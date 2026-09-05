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

package de.jpx3.intave.user;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.diagnostic.MemoryWatchdog;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.mitigate.HurttimeModifier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class UserRepository {
  private static final Map<UUID, User> repository = MemoryWatchdog.watch("users", new ConcurrentHashMap<>());
  private static final User fallbackUser = UserFactory.createFallback();

  // used to load the class on startup
  public static void setup() {
    ShutdownTasks.add(UserRepository::die);
  }

  public static void registerUser(Player player) {
    repository.put(player.getUniqueId(), UserFactory.createUserFor(player));
    User user = repository.get(player.getUniqueId());
    if (IntaveControl.RESET_HURT_TIME_ON_JOIN) {
      Synchronizer.synchronizeDelayed(user, () -> {
        HurttimeModifier.setNoDamageTicksOf(player, 20);
      }, 20);
    }
    if (IntaveControl.GIVE_RIPTIDE_V_TRIDENT_ON_JOIN && MinecraftVersions.VER1_14_0.atOrAbove()) {
      Synchronizer.synchronizeDelayed(user, () -> {
        if (!player.getInventory().contains(Material.getMaterial("TRIDENT"))) {
          ItemStack trident = new ItemStack(Material.getMaterial("TRIDENT"));
          trident.addUnsafeEnchantment(Enchantment.getByName("RIPTIDE"), 5);
          player.getInventory().addItem(trident);
        }
      }, 20);
    }
  }

  public static void manuallyRegisterUser(Player player, User user) {
    repository.put(player.getUniqueId(), user);
  }

  public static boolean hasUser(Player player) {
    return repository.containsKey(player.getUniqueId());
  }

  public static void unregisterUser(Player player) {
    if (hasUser(player)) {
      userOf(player).unregister();
    }
    repository.remove(player.getUniqueId());
  }

  public static @NotNull User userOf(@Nullable Player player) {
    if (player == null) {
      return fallbackUser;
    }
    User user = repository.get(player.getUniqueId());
    return user != null ? user : fallbackUser;
  }

  public static @NotNull User userOf(@Nullable UUID uuid) {
    if (uuid == null) {
      return fallbackUser;
    }
    User user = repository.get(uuid);
    return user != null ? user : fallbackUser;
  }

  public static void applyOnAll(Consumer<? super User> consumer) {
    for (User user : repository.values()) {
      consumer.accept(user);
    }
  }

  public static void applyOnOnlineUsers(Consumer<? super User> consumer) {
    for (Player player : Bukkit.getOnlinePlayers()) {
      User user = repository.get(player.getUniqueId());
      if (user != null) {
        consumer.accept(user);
      }
    }
  }

  public static void die() {
    unregisterAll();
    repository.clear();
  }

  private static void unregisterAll() {
    for (UUID uuid : repository.keySet()) {
      Player player = Bukkit.getPlayer(uuid);
      if (player != null) {
        unregisterUser(player);
      }
    }
  }

	public static User fallback() {
    return fallbackUser;
	}
}
