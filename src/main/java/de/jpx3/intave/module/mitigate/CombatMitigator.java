package de.jpx3.intave.module.mitigate;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.MessageChannelSubscriptions;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.PunishmentMetadata;
import de.jpx3.intave.user.meta.PunishmentMetadata.AttackNerfer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public final class CombatMitigator extends Module {

  @BukkitEventSubscription
  public void receiveAttack(EntityDamageByEntityEvent event) {
    Entity attacker = event.getDamager();
    if (!(attacker instanceof Player) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
      return;
    }
    Player player = (Player) attacker;
    PunishmentMetadata punishmentData = UserRepository.userOf(player).meta().punishment();
    for (AttackNerfer attackNerfer : punishmentData.availableAttackNerfer()) {
      if (attackNerfer.active() && !attackNerfer.inverseEvent()) {
        attackNerfer.executor().accept(event);
      }
    }
    Entity attacked = event.getEntity();
    if (!(attacked instanceof Player)) {
      return;
    }
    Player attackedPlayer = (Player) attacked;
    punishmentData = UserRepository.userOf(attackedPlayer).meta().punishment();
    for (AttackNerfer attackNerfer : punishmentData.availableAttackNerfer()) {
      if (attackNerfer.active() && attackNerfer.inverseEvent()) {
        attackNerfer.executor().accept(event);
      }
    }
  }

  @Deprecated
  public void mitigate(User user, AttackNerfStrategy type, String checkId) {
    Synchronizer.synchronize(() -> {
      AttackNerfer nerfer = user.meta().punishment().nerferOfType(type);
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
    String message = ChatColor.RED + "[CM] Applied " + attackNerfer.name() + " combat nerfer on " + player.getName() + " (dmc" + checkId + ")";

    if (IntaveControl.DEBUG_HEURISTICS && !plugin.sibylIntegrationService().isAuthenticated(player)) {
      player.sendMessage(message);
    }

    if (IntaveControl.GOMME_MODE) {
      IntaveLogger.logger().printLine("[Intave] " + ChatColor.stripColor(message));
    }

    for (Player authenticatedPlayer : MessageChannelSubscriptions.sibylReceiver()/*Bukkit.getOnlinePlayers()*/) {
      if (plugin.sibylIntegrationService().isAuthenticated(authenticatedPlayer)) {
        authenticatedPlayer.sendMessage(message);
      }
    }
  }
}