package de.jpx3.intave.user;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class UserLifetimeService implements BukkitEventSubscriber {
  public UserLifetimeService(IntavePlugin plugin) {
    plugin.eventLinker().registerEventsIn(this);
    synchronizePlayers();
  }

  private void synchronizePlayers() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (UserRepository.userOf(player) == null) {
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
    Synchronizer.synchronizeDelayed(user::delayedSetup, 10);
  }

  @BukkitEventSubscription(priority = EventPriority.HIGHEST)
  public void receiveQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    UserRepository.unregisterUser(player);
  }
}