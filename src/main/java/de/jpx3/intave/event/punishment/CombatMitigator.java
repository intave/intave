package de.jpx3.intave.event.punishment;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.event.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaPunishmentData;
import de.jpx3.intave.user.UserMetaPunishmentData.AttackNerfer;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class CombatMitigator implements BukkitEventSubscriber {
  private final IntavePlugin plugin;

  public CombatMitigator(IntavePlugin plugin) {
    this.plugin = plugin;
    plugin.eventLinker().registerEventsIn(this);
  }

  @BukkitEventSubscription
  public void receiveAttack(EntityDamageByEntityEvent event) {
    Entity attacker = event.getDamager();
    if (!(attacker instanceof Player)) {
      return;
    }
    Player player = (Player) attacker;
    UserMetaPunishmentData punishmentData = UserRepository.userOf(player).meta().punishmentData();
    for (AttackNerfer attackNerfer : punishmentData.availableAttackNervers()) {
      if (attackNerfer.active()) {
        attackNerfer.executor().accept(event);
      }
    }
  }

  @Deprecated
  public void mitigate(User user, AttackNerfStrategy type) {
    Synchronizer.synchronize(() -> {
      AttackNerfer nerfer = user.meta().punishmentData().nerferOfType(type);
      sendSibylNotify(user, nerfer);
      nerfer.activate();
    });
  }

  @Native
  private void sendSibylNotify(User user, AttackNerfer attackNerfer) {
    if (attackNerfer.active()) {
      return;
    }
    Player player = user.player();
    String message = ChatColor.RED + "[DC] Performed " + attackNerfer.name() + " damage cancel on " + player.getName();
    if (IntaveControl.DEBUG_HEURISTICS && !plugin.sibylIntegrationService().isAuthenticated(player)) {
      player.sendMessage(message);
    }
    for (Player authenticatedPlayer : Bukkit.getOnlinePlayers()) {
      if (plugin.sibylIntegrationService().isAuthenticated(authenticatedPlayer)) {
        authenticatedPlayer.sendMessage(message);
      }
    }
  }
}