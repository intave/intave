package de.jpx3.intave.event.punishment;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.event.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaPunishmentData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class AttackCancelService implements BukkitEventSubscriber {
  private final IntavePlugin plugin;

  public AttackCancelService(IntavePlugin plugin) {
    this.plugin = plugin;
    plugin.eventLinker().registerEventsIn(this);
  }

  @BukkitEventSubscription
  public void receiveAttack(EntityDamageByEntityEvent event) {
    Entity damager = event.getDamager();
    if (!(damager instanceof Player)) {
      return;
    }
    Player player = (Player) damager;
    User user = UserRepository.userOf(player);
    UserMetaPunishmentData punishmentData = user.meta().punishmentData();
    for (UserMetaPunishmentData.DamageCancel damageCancel : punishmentData.damageCancels()) {
      if (damageCancel.active()) {
        damageCancel.executor().accept(event);
      }
    }
  }

  public void requestDamageCancel(
    User user,
    AttackCancelType type
  ) {
    Synchronizer.synchronize(() -> {
      UserMetaPunishmentData punishmentData = user.meta().punishmentData();
      UserMetaPunishmentData.DamageCancel damageCancel = punishmentData.damageCancelOfType(type);
      if (!damageCancel.active()) {
        sendSibylNotify(user, damageCancel);
      }
      damageCancel.activate();
    });
  }

  @Native
  private void sendSibylNotify(
    User user,
    UserMetaPunishmentData.DamageCancel damageCancel
  ) {
    if (damageCancel.active()) {
      return;
    }

    Player player = user.player();
    String message = ChatColor.RED + "[DC] Performed " + damageCancel.name() + " damage cancel on " + player.getName();

    if (IntaveControl.DEBUG_HEURISTICS && ! plugin.sibylIntegrationService().isAuthenticated(player)) {
      player.sendMessage(message);
    }

    for (Player authenticatedPlayer : Bukkit.getOnlinePlayers()) {
      if (plugin.sibylIntegrationService().isAuthenticated(authenticatedPlayer)) {
        authenticatedPlayer.sendMessage(message);
      }
    }
  }
}