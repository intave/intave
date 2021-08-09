package de.jpx3.intave.user;

import org.bukkit.entity.Player;

public final class UserFactory {
  public static User newFallback() {
    return new FallbackUser();
  }

  public static User newFor(Player player) {
    return new PlayerUser(player);
  }
}
