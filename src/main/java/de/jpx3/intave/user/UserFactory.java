package de.jpx3.intave.user;

import org.bukkit.entity.Player;

import java.util.function.BiFunction;

public final class UserFactory {
  public static User createFallback() {
    return new FallbackUser();
  }

  public static User createUserFor(Player player) {
    return new PlayerUser(player);
  }

  public static User createTestUserFor(Player player) {
    return new TestUser(player, (usr, s) -> null);
  }

  public static User createTestUserFor(Player player, int protocolVersion) {
    return new TestUser(player, (usr, s) -> {
      if (s.equals("protocolVersion")) {
        return protocolVersion;
      }
      return null;
    });
  }

  public static User createTestUserFor(Player player, BiFunction<User, String, Object> function) {
    return new TestUser(player, function);
  }
}
