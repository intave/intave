package de.jpx3.intave.module.player;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.annotate.refactoring.IdoNotBelongHere;
import de.jpx3.intave.annotate.refactoring.SplitMeUp;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.trace.Caller;
import de.jpx3.intave.klass.trace.PluginInvocation;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.permission.BukkitPermissionCheck;
import de.jpx3.intave.version.DurationTranslator;
import de.jpx3.intave.version.IntaveVersion;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.IntaveControl.DISALLOW_ALL_BLOCK_PLACEMENTS_WITH_EVENT;
import static org.bukkit.event.EventPriority.MONITOR;

@SplitMeUp
public final class MiscBukkitEvents extends Module {
  @BukkitEventSubscription
  @IdoNotBelongHere
  public void on(PlayerJoinEvent join) {
    Player player = join.getPlayer();
    boolean hasNotificationPermission = BukkitPermissionCheck.permissionCheck(player, "intave.command") && !BukkitPermissionCheck.permissionCheck(player, "intave.command.noupdate");
    if (!hasNotificationPermission) {
      return;
    }
    String currentVersion = IntavePlugin.version();
    IntaveVersion version = plugin.versions().versionInformation(currentVersion);
    if (version == null) {
      sendPrefixedMessage(ChatColor.YELLOW + "This server is running an unlisted version of Intave (" + currentVersion + ")", player);
      sendPrefixedMessage(ChatColor.YELLOW + "It is possible that bugs occur", player);
    } else {
      if (version.typeClassifier() == IntaveVersion.Status.OUTDATED) {
        long duration = System.currentTimeMillis() - version.release();
        String durationAsString = DurationTranslator.translateDuration(duration);

        sendPrefixedMessage(ChatColor.RED + "This server is running an outdated version of Intave (" + durationAsString + " old)", player);
        if (!Bukkit.getPluginManager().isPluginEnabled("IntaveBootstrap")) {
          sendPrefixedMessage(ChatColor.RED + "Too lazy? Stay up-to-date automatically with IntaveBootstrap", player);
        }
        sendPrefixedMessage(ChatColor.RED + "We hope you understand why updating your *security* software might be important.", player);
      }
    }
  }

  @BukkitEventSubscription
  @IdoNotBelongHere
  public void on(PlayerTeleportEvent teleport) {
    if (IntaveControl.DEBUG_TELEPORT_CAUSE_AND_CAUSER) {
      PluginInvocation pluginInvocation = Caller.pluginInfo();
      String pluginClass = pluginInvocation == null ? "no other plugin" : pluginInvocation.className();
      teleport.getPlayer().sendMessage("Teleport " + teleport.getCause() + " " + teleport.getTo() + " by " + pluginClass);
    }
  }

  @BukkitEventSubscription
  public void on(BlockPlaceEvent place) {
    if (DISALLOW_ALL_BLOCK_PLACEMENTS_WITH_EVENT) {
      place.setCancelled(true);
    }
  }

  @BukkitEventSubscription
  @IdoNotBelongHere
  public void on(WorldUnloadEvent unloadEvent) {
    World world = unloadEvent.getWorld();
    GarbageCollector.clear(world);
//    GarbageCollector.clear(world.getUID());
    GarbageCollector.clearIf(o -> o instanceof Location && ((Location) o).getWorld().equals(world));
  }

  @BukkitEventSubscription(priority = MONITOR)
  @IdoNotBelongHere
  public void on(PlayerQuitEvent quit) {
    Player player = quit.getPlayer();
    GarbageCollector.clear(player);
    GarbageCollector.clear(player.getUniqueId());
  }

  /*
   * fixes a bug where players drop their sword whilst blocking, tricking the server into letting them constantly block - even whilst attacking
   */
  @BukkitEventSubscription(ignoreCancelled = true)
  @IdoNotBelongHere
  public void on(PlayerDropItemEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ItemStack item = player.getItemOnCursor();
    if (ItemProperties.isSwordItem(item) && !ViaVersionAdapter.ignoreBlocking(user.player())) {
      Material material = item.getType();
      user.meta().inventory().releaseItemNextTick = true;
      user.meta().inventory().releaseItemType = material;
    }
  }

  @BukkitEventSubscription
  public void on(EntityShootBowEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      return;
    }
    User user = UserRepository.userOf((Player) event.getEntity());
    InventoryMetadata inventory = user.meta().inventory();
    if (inventory.blockNextArrow) {
      boolean applyArrowBlock = System.currentTimeMillis() - inventory.lastBlockArrowRequest < 800L;
      if (applyArrowBlock) {
        event.setCancelled(true);
      }
      inventory.blockNextArrow = false;
    }
  }

//  @BukkitEventSubscription
//  public void on(EntityDamageByEntityEvent event) {
//    if (!(event.getDamager() instanceof Player)) {
//      return;
//    }
//    double predAttackDamage = DamageModify.attackDamageOf((Player) event.getDamager());
//    ItemStack heldItem = UserRepository.userOf((Player) event.getDamager()).meta().inventory().heldItem();
//    predAttackDamage += DamageModify.sharpnessDamageOf(heldItem);
//    double actualAttackDamage = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
//    System.out.println("ATTACK " + event.getDamager() + " -> " + event.getEntity() + " " + predAttackDamage +"/"+actualAttackDamage);
//  }

//  @BukkitEventSubscription
//  public void on(PlayerAttackEntityCooldownResetEvent event) {
//    System.out.println("RESET " + event.getPlayer() + " " + event.getCooledAttackStrength());
////    Thread.dumpStack();
//  }

  @BukkitEventSubscription
  public void on(PlayerItemConsumeEvent consumption) {
    User user = UserRepository.userOf(consumption.getPlayer());
    InventoryMetadata inventory = user.meta().inventory();
    if (System.currentTimeMillis() - inventory.lastFoodConsumptionBlockRequest < 800L) {
      consumption.setCancelled(true);
    }
  }

  @IdoNotBelongHere
  private void sendPrefixedMessage(String message, CommandSender target) {
    if (!Bukkit.isPrimaryThread()) {
      Synchronizer.synchronize(() -> sendPrefixedMessage(message, target));
      return;
    }
    target.sendMessage(IntavePlugin.prefix() + message);
  }
}
