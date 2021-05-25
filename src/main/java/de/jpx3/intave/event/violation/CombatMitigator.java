package de.jpx3.intave.event.violation;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.event.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.logging.IntaveLogger;
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
import org.bukkit.event.entity.EntityDamageEvent;

public final class CombatMitigator implements BukkitEventSubscriber {
  private final IntavePlugin plugin;

  public CombatMitigator(IntavePlugin plugin) {
    this.plugin = plugin;
    plugin.eventLinker().registerEventsIn(this);
  }

  @BukkitEventSubscription
  public void receiveAttack(EntityDamageByEntityEvent event) {
    Entity attacker = event.getDamager();
    if (!(attacker instanceof Player) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
      return;
    }
    Player player = (Player) attacker;
    UserMetaPunishmentData punishmentData = UserRepository.userOf(player).meta().punishmentData();
    for (AttackNerfer attackNerfer : punishmentData.availableAttackNervers()) {
      if (attackNerfer.active() && !attackNerfer.inverseEvent()) {
        attackNerfer.executor().accept(event);
      }
    }
    Entity attacked = event.getEntity();
    if(!(attacked instanceof Player)) {
      return;
    }
    Player attackedPlayer = (Player) attacked;
    punishmentData = UserRepository.userOf(attackedPlayer).meta().punishmentData();
    for (AttackNerfer attackNerfer : punishmentData.availableAttackNervers()) {
      if (attackNerfer.active() && attackNerfer.inverseEvent()) {
        attackNerfer.executor().accept(event);
      }
    }
  }

  @Deprecated
  public void mitigate(User user, AttackNerfStrategy type, String checkId) {
    Synchronizer.synchronize(() -> {
      AttackNerfer nerfer = user.meta().punishmentData().nerferOfType(type);
      notify(user, nerfer, checkId);
      nerfer.activate();
    });
  }

  @Native
  private void notify(User user, AttackNerfer attackNerfer, String checkId) {
    if (attackNerfer.active()) {
      return;
    }

    Player player = user.player();
    String message = ChatColor.RED + "[CM] Applied " + attackNerfer.name() + " combat nerfer on " + player.getName() + " (" + checkId + ")";

    if (IntaveControl.DEBUG_HEURISTICS && !plugin.sibylIntegrationService().isAuthenticated(player)) {
      player.sendMessage(message);
    }

    if (IntaveControl.GOMME_MODE) {
      IntaveLogger.logger().pushPrintln("[Intave] " + ChatColor.stripColor(message));
    }

    for (Player authenticatedPlayer : Bukkit.getOnlinePlayers()) {
      if (plugin.sibylIntegrationService().isAuthenticated(authenticatedPlayer)) {
        authenticatedPlayer.sendMessage(message);
      }
    }
  }
}