package de.jpx3.intave.tools.placeholder;

import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.access.TrustFactor;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

import java.util.Map;

public final class PlayerContext extends PlaceholderContext {
  private final User user;

  public PlayerContext(User user) {
    this.user = user;
  }

  @Override
  public Map<String, String> replacements() {
    if(!user.hasOnlinePlayer()) {
      return ImmutableMap.of();
    }
    Player player = user.player();
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

    TrustFactor trustFactor = user.trustFactor();
    String trustFactorName = trustFactor.name().toLowerCase().replace("_", "");
    builder.put("trust", trustFactorName);
    builder.put("trust-color", trustFactor.chatColor() + trustFactorName);

    builder.put("latency", String.valueOf(user.latency()));
    builder.put("jitter", String.valueOf(user.latencyJitter()));
    builder.put("player", player.getName());
//    builder.put("version", MinecraftVersionResolver.fancyProtocolVersionOf(player));
    builder.put("world", player.getWorld().getName());


    return builder.build();
  }
}
