package de.jpx3.intave.user;

import org.bukkit.entity.Player;

public final class UserFactory {
  public static User createFallback() {
    return new FallbackUser();
  }

  public static User createUserFor(Player player) {
    return new PlayerUser(player);
  }

  public static User createTestUserFor(Player player, int protocolVersion) {
    return new TestUser(player, protocolVersion);
  }
}
