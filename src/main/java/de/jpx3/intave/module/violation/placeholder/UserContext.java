package de.jpx3.intave.module.violation.placeholder;

import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

import java.util.Map;

public final class UserContext implements PlaceholderContext {
  private final User user;

  public UserContext(User user) {
    this.user = user;
  }

  @Override
  public Map<String, String> replacements() {
    if (!user.hasPlayer()) {
      return ImmutableMap.of();
    }
    Player player = user.player();
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

    TrustFactor trustFactor = user.trustFactor();
    builder.put("trust", trustFactor.baseName());
    builder.put("trust-color", trustFactor.coloredBaseName());
    builder.put("latency", String.valueOf(user.latency()));
    builder.put("jitter", String.valueOf(user.latencyJitter()));
    builder.put("version", user.meta().protocol().versionString());
    builder.put("world", player.getWorld().getName());

    return builder.build();
  }
}
