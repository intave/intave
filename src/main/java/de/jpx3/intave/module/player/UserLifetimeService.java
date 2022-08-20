package de.jpx3.intave.module.player;

import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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

  @BukkitEventSubscription(priority = EventPriority.LOWEST)
  public void receiveJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    UserRepository.registerUser(player);
    User user = UserRepository.userOf(player);
    Synchronizer.synchronizeDelayed(user::delayedSetup, 20);
  }

  @BukkitEventSubscription(priority = EventPriority.HIGHEST)
  public void receiveQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    UserRepository.unregisterUser(player);
  }
}