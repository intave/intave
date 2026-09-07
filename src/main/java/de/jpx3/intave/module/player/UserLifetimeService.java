package de.jpx3.intave.module.player;

import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.LOGIN;

public final class UserLifetimeService extends Module {
  public void enable() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (!UserRepository.userOf(player).hasPlayer()) {
        UserRepository.registerUser(player);
      }
      User user = UserRepository.userOf(player);
      user.delayedSetup();
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsOut = {
      LOGIN
    }
  )
  public void receiveLogin(PacketEvent event) {
    Player player = event.getPlayer();
    setupUser(player);
  }

  /**
   * PacketEvents entry point for the same packet. The body never reads a packet field, it only
   * needs the moment the join packet is sent, so no view is required here.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.LOWEST,
    packetsOut = {
      LOGIN
    }
  )
  public void receiveLogin(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    setupUser(player);
  }

  @BukkitEventSubscription(priority = EventPriority.LOWEST)
  public void receiveJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    if (!UserRepository.hasUser(player)) {
      setupUser(player);
    }
  }

  private void setupUser(Player player) {
    UserRepository.registerUser(player);
    User user = UserRepository.userOf(player);
    Synchronizer.synchronizeDelayed(user, user::delayedSetup, 20);
  }

  @BukkitEventSubscription(priority = EventPriority.HIGHEST)
  public void receiveQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    UserRepository.unregisterUser(player);
  }
}
