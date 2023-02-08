package de.jpx3.intave.module.mitigate;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.connect.sibyl.SibylMessageTransmitter;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.MessageChannelSubscriptions;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.PunishmentMetadata;
import de.jpx3.intave.user.meta.PunishmentMetadata.AttackNerfer;
import de.jpx3.intave.user.storage.NerferStorage;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static de.jpx3.intave.access.player.trust.TrustFactor.YELLOW;

public final class CombatMitigator extends Module {

  @BukkitEventSubscription
  public void on(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    user.onStorageReady(storage -> storageLoad(user));
  }

  @BukkitEventSubscription
  public void on(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    user.onStorageReady(storage -> storageSave(user));
  }

  private static final long ONE_HOUR_IN_MILLIS = 1000 * 60 * 60;

  public void storageLoad(User user) {
    NerferStorage nerferStorage = user.storageOf(NerferStorage.class);
    if (System.currentTimeMillis() - nerferStorage.savedAt() > ONE_HOUR_IN_MILLIS) {
      nerferStorage.clearNerfers();
      return;
    }
    Map<String, Long> nerfers = nerferStorage.nerfers();
    nerfers.forEach((name, expires) -> {
      AttackNerfStrategy nerfStrategy = AttackNerfStrategy.byName(name);
      if (nerfStrategy == null) {
        return;
      }
      AttackNerfer nerfer = user.meta().punishment().nerferOfType(nerfStrategy);
      notify(user, nerfer, "00");
      nerfer.activateUntil(expires);
    });
  }

  public void storageSave(User user) {
    NerferStorage nerferStorage = user.storageOf(NerferStorage.class);
    for (AttackNerfer activeNerfer : user.meta().punishment().activeNerfers()) {
      nerferStorage.addNerfer(activeNerfer.strategy().name(), activeNerfer.expiry());
    }
  }

  @BukkitEventSubscription(priority = EventPriority.LOWEST)
  public void receiveAttack(EntityDamageByEntityEvent event) {
    Entity attacker = event.getDamager();
    if (!(attacker instanceof Player) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || !(event.getEntity() instanceof LivingEntity)) {
      return;
    }
    Player player = (Player) attacker;
    PunishmentMetadata punishmentData = UserRepository.userOf(player).meta().punishment();
    for (AttackNerfer attackNerfer : punishmentData.allNerfers()) {
      if (attackNerfer.active() && !attackNerfer.inverseEvent()) {
        attackNerfer.executor().accept(event);
      }
    }

    if (IntaveControl.DEBUG_ATTACK_DAMAGE_MODIFIERS) {
      player.sendMessage("");
      for (EntityDamageEvent.DamageModifier value : EntityDamageEvent.DamageModifier.values()) {
        double damage = event.getDamage(value);
        if (damage != 0) {
          player.sendMessage( value + " = " + damage);
        }
      }
    }

    Entity attacked = event.getEntity();
    if (!(attacked instanceof Player)) {
      return;
    }

    Player attackedPlayer = (Player) attacked;
    punishmentData = UserRepository.userOf(attackedPlayer).meta().punishment();
    for (AttackNerfer attackNerfer : punishmentData.allNerfers()) {
      if (attackNerfer.active() && attackNerfer.inverseEvent()) {
        attackNerfer.executor().accept(event);
      }
    }
  }

  @BukkitEventSubscription
  public void on(AsyncPlayerChatEvent chat) {
    if (IntaveControl.GOMME_MODE) {
      Player player = chat.getPlayer();
      User user = UserRepository.userOf(player);
      String message = chat.getMessage();
      List<String> badWords = Arrays.asList("augustus", "ryu", "haze yt", "icarus", "eject");
      for (String badWord : badWords) {
        if (message.toLowerCase().contains(badWord) && !user.trustFactor().atLeast(YELLOW)) {
          mitigatePermanently(user, AttackNerfStrategy.CRITICALS, "64");
          mitigatePermanently(user, AttackNerfStrategy.BLOCKING, "64");
          mitigatePermanently(user, AttackNerfStrategy.BURN_LONGER, "64");
          break;
        }
      }
    }
  }

  @Deprecated
  public void mitigate(User user, AttackNerfStrategy type, String checkId) {
    Synchronizer.synchronize(() -> {
      AttackNerfer nerfer = user.meta().punishment().nerferOfType(type);
      boolean wasActive = nerfer.active();
      nerfer.activate();
      if (!wasActive) {
        notify(user, nerfer, checkId);
      }
    });
  }

  @Deprecated
  public void mitigateOnce(User user, AttackNerfStrategy type, String checkId) {
    Synchronizer.synchronize(() -> {
      AttackNerfer nerfer = user.meta().punishment().nerferOfType(type);
      boolean wasActive = nerfer.active();
      nerfer.activateOnce();
      if (!wasActive) {
        notify(user, nerfer, checkId);
      }
    });
  }

  public void mitigatePermanently(User user, AttackNerfStrategy type, String checkId) {
    Synchronizer.synchronize(() -> {
      AttackNerfer nerfer = user.meta().punishment().nerferOfType(type);
      boolean wasActive = nerfer.active();
      nerfer.activatePermanently();
      if (!wasActive) {
        notify(user, nerfer, checkId, true);
      }
    });
  }

  @BukkitEventSubscription
  public void on(EntityCombustEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      return;
    }
    Player player = (Player) event.getEntity();
    User user = UserRepository.userOf(player);
    if (user.meta().punishment().nerferOfType(AttackNerfStrategy.BURN_LONGER).active()) {
      event.setDuration((int) (event.getDuration() * 1.3d));
    }
  }

  private void notify(User user, AttackNerfer attackNerfer, String checkId) {
    notify(user, attackNerfer, checkId, false);
  }

  @Native
  private void notify(User user, AttackNerfer attackNerfer, String checkId, boolean hide) {
    if (!attackNerfer.active()) {
      return;
    }

    Player player = user.player();
    long expiry = attackNerfer.expiry();

    String durationText;
    if (expiry == Long.MAX_VALUE) {
      durationText = "permanently";
    } else {
      durationText = "for " + MathHelper.formatDouble((expiry - System.currentTimeMillis()) / 1000d, 2) + "s";
    }
    String message = ChatColor.RED + "[CM] Applied " + attackNerfer.name() + " combat nerfer on " + player.getName() + " (dmc" + checkId + ") " + durationText;

    if (IntaveControl.DEBUG_HEURISTICS && !plugin.sibyl().isAuthenticated(player)) {
      player.sendMessage(message);
    }

    if (IntaveControl.GOMME_MODE && !hide) {
      IntaveLogger.logger().printLine("[Intave] " + ChatColor.stripColor(message));
    }

    for (Player authenticatedPlayer : MessageChannelSubscriptions.sibylReceivers()/*Bukkit.getOnlinePlayers()*/) {
      if (plugin.sibyl().isAuthenticated(authenticatedPlayer)) {
        SibylMessageTransmitter.sendMessage(authenticatedPlayer, message);
      }
    }
  }
}