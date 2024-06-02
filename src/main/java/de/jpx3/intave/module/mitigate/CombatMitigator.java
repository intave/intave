package de.jpx3.intave.module.mitigate;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.connect.sibyl.SibylMessageTransmitter;
import de.jpx3.intave.diagnostic.natives.NativeCheck;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityVelocityReader;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.MessageChannelSubscriptions;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.PunishmentMetadata;
import de.jpx3.intave.user.meta.PunishmentMetadata.AttackNerfer;
import de.jpx3.intave.user.storage.NerferStorage;
import org.bukkit.Bukkit;
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
import java.util.concurrent.ThreadLocalRandom;

import static de.jpx3.intave.access.player.trust.TrustFactor.ORANGE;
import static de.jpx3.intave.access.player.trust.TrustFactor.RED;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.RECEIVE_MORE_KNOCKBACK;

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
      nerferStorage.addNerfer(activeNerfer.strategy().typeName(), activeNerfer.expiry());
    }
  }

  // [-0.36565704  1.19629782 -1.41612646  0.65648595]
  private final double[] velocityPolynomialCoefficients = new double[] {
    -0.36565704, 1.19629782, -1.41612646, 0.65648595
  };

  private double polyEval(double x) {
    /*
        for pv in p:
        y = y * x + pv
     */
    double y = 0;
    for (double pv : velocityPolynomialCoefficients) {
      y = y * x + pv;
    }
    return y;
  }

  @PacketSubscription(
    packetsIn = PacketId.Client.ARM_ANIMATION
  )
  public void onArmAnimationPacket(
    User user, PacketEvent event
  ) {
    user.meta().punishment().lastSwing = System.currentTimeMillis();
  }

  @PacketSubscription(
    packetsOut = PacketId.Server.ENTITY_VELOCITY
  )
  public void onVelocityPacket(
    User user, PacketEvent event, EntityVelocityReader reader
  ) {
    int entityId = user.player().getEntityId();
    if (reader.entityId() != entityId) {
      return;
    }
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      User otherUser = UserRepository.userOf(onlinePlayer);
      int lastAttacked = otherUser.meta().punishment().lastLowVelocityApplyId;
      if (lastAttacked != entityId) {
        continue;
      }

      otherUser.meta().punishment().lastLowVelocityApplyId = -1;

      long sinceLastSwing = System.currentTimeMillis() - user.meta().punishment().lastSwing;

      Position legitPosition = user.meta().movement().position();
      Position cheatPosition = otherUser.meta().movement().position();

      Rotation rotation = legitPosition.rotationTo(cheatPosition);
      float yawRequired = rotation.yaw();
      float actualYaw = user.meta().movement().rotationYaw();

      double yawDiff = MathHelper.distanceInDegrees(yawRequired, actualYaw);
      if (yawDiff > 60 || sinceLastSwing > 1000) {
        continue;
      }

      double motionX = reader.motionX();
      double motionZ = reader.motionZ();

      double ratio = 1;
      int reduceTicks = ThreadLocalRandom.current().nextInt(0, 3);
      if (reduceTicks == 0) {
        ratio = 0.8;
      }
      for (int i = 0; i < reduceTicks; i++) {
        ratio *= 0.6;
      }
      reader.setMotionX(motionX * ratio);
      reader.setMotionZ(motionZ * ratio);
      return;
    }

    PunishmentMetadata punishment = user.meta().punishment();
    if (
      punishment.nerferOfType(RECEIVE_MORE_KNOCKBACK).active() &&
      punishment.velocityIncreaseTokens > 0
    ) {
      double motionX = reader.motionX();
      double motionZ = reader.motionZ();

      double horizontal = Math.sqrt(motionX * motionX + motionZ * motionZ);
      double velocityAdd = polyEval(MathHelper.minmax(0, horizontal, 1));

      // randomize a bit
      velocityAdd += ThreadLocalRandom.current().nextGaussian() * 0.333;

      double factor = (MathHelper.minmax(0, velocityAdd,0.6) + 1);
      reader.setMotionX(motionX * factor);
      reader.setMotionZ(motionZ * factor);
      punishment.velocityIncreaseTokens--;
    }

    long lastVelocityIncreaseReset = punishment.lastVelocityIncreaseReset;
    // reset 1 token per 10 seconds
    double tokenGain = (System.currentTimeMillis() - lastVelocityIncreaseReset) / 10000d;
    punishment.velocityIncreaseTokens = MathHelper.minmax(-1, punishment.velocityIncreaseTokens + tokenGain, 6);
    punishment.lastVelocityIncreaseReset = System.currentTimeMillis();
  }

  @BukkitEventSubscription(priority = EventPriority.LOWEST)
  public void receiveAttack(EntityDamageByEntityEvent event) {
    Entity attacker = event.getDamager();
    Entity attacked = event.getEntity();
    if (!(attacker instanceof Player) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || !(event.getEntity() instanceof LivingEntity)) {
      return;
    }
    Player player = (Player) attacker;
    PunishmentMetadata punishmentData = UserRepository.userOf(player).meta().punishment();

    boolean attackerHasRedTrust = UserRepository.userOf(player).trustFactor().atOrBelow(RED);
    boolean attackedHasRedTrust = (attacked instanceof Player) && UserRepository.userOf((Player) attacked).trustFactor().atOrBelow(RED);

    for (AttackNerfer attackNerfer : punishmentData.allNerfers()) {
      if (attackNerfer.active() && !attackedHasRedTrust && !attackNerfer.inverseEvent()) {
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

    if (!(attacked instanceof Player)) {
      return;
    }

    if (((Player) attacked).getHealth() - event.getFinalDamage() < 0) {
      punishmentData.velocityIncreaseTokens = 6;
    }

    Player attackedPlayer = (Player) attacked;
    User attackedUser = UserRepository.userOf(attackedPlayer);

    punishmentData = attackedUser.meta().punishment();
    for (AttackNerfer attackNerfer : punishmentData.allNerfers()) {
      if (attackNerfer.active() && !attackerHasRedTrust && attackNerfer.inverseEvent()) {
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
      List<String> badWords = Arrays.asList("augustus", "augus", "gustus", "ryu", "haze yt", "icarus", "eject");
      for (String badWord : badWords) {
        if (message.toLowerCase().contains(badWord) && user.trustFactor().atOrBelow(ORANGE)) {
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
    if (type == AttackNerfStrategy.BLOCKING && user.meta().protocol().combatUpdate()) {
      type = AttackNerfStrategy.HT_LIGHT;
    }
    AttackNerfStrategy finalType = type;
    Synchronizer.synchronize(() -> {
      AttackNerfer nerfer = user.meta().punishment().nerferOfType(finalType);
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
  private static void notify(User user, AttackNerfer attackNerfer, String checkId, boolean hide) {
    if (NativeCheck.checkActive()) {
      return;
    }
    IntavePlugin plugin = IntavePlugin.singletonInstance();

    if (!attackNerfer.active()) {
      return;
    }

    // nope
//    if (attackNerfer.strategy() == HT_SPOOF) {
//      return;
//    }

    Player player = user.player();
    long expiry = attackNerfer.expiry();

    String durationText;
    if (expiry == Long.MAX_VALUE) {
      durationText = "permanently";
    } else {
      durationText = "for " + MathHelper.formatDouble((expiry - System.currentTimeMillis()) / 1000d, 2) + "s";
    }

    if (IntaveControl.DEBUG_CMS) {
      user.player().sendMessage(ChatColor.RED + "[Intave] " + ChatColor.GRAY + "Applied " + attackNerfer.name() + " combat nerfer " + durationText);
    }

    String message = ChatColor.RED + "[CM] Applied " + attackNerfer.name() + " combat nerfer on " + player.getName() + " (dmc" + checkId + ") " + durationText;

    if (IntaveControl.DEBUG_HEURISTICS && !plugin.sibyl().isAuthenticated(player)) {
      player.sendMessage(message);
    }

    if (attackNerfer.strategy().showToUsers() && !attackNerfer.hidden() && !hide) {
      String kMessage = IntavePlugin.prefix() + " Issued " + attackNerfer.strategy().description() + " on " + ChatColor.RED + player.getName() + ChatColor.GRAY + "/" + user.trustFactor().coloredBaseName() + ChatColor.GRAY + " " + durationText;
      for (Player player1 : MessageChannelSubscriptions.receiverOf(MessageChannel.COMBAT_MODIFIERS)) {
        User user1 = UserRepository.userOf(player1);
        if (user1.receives(MessageChannel.COMBAT_MODIFIERS)) {
          Synchronizer.synchronizeDelayed(() -> {
            player1.sendMessage(kMessage);
          }, 4);
        }
      }
    }

    if (IntaveControl.GOMME_MODE /*&& !hide*/) {
      IntaveLogger.logger().printLine("[Intave] " + ChatColor.stripColor(message));
      IntavePlugin.singletonInstance().logTransmittor()
        .addPlayerLog(player, message);
    }

    for (Player authenticatedPlayer : MessageChannelSubscriptions.sibylReceivers()/*Bukkit.getOnlinePlayers()*/) {
      if (plugin.sibyl().isAuthenticated(authenticatedPlayer)) {
        SibylMessageTransmitter.sendMessage(authenticatedPlayer, message);
      }
    }
  }

  static {
    NativeCheck.registerNative(() -> {
      notify(null, null, "00", true);
    });
  }
}