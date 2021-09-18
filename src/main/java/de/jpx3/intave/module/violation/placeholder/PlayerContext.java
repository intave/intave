package de.jpx3.intave.module.violation.placeholder;

import com.google.common.collect.ImmutableMap;
import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;

public final class PlayerContext implements PlaceholderContext {
  private final String playerName;
  private final UUID uuid;
  private final InetAddress address;

  public PlayerContext(String playerName, UUID uuid, InetAddress address) {
    this.playerName = playerName;
    this.uuid = uuid;
    this.address = address;
  }

  @Override
  public Map<String, String> replacements() {
    return ImmutableMap.of(
      "player", String.valueOf(playerName),
      "playername", String.valueOf(playerName),
      "uuid", String.valueOf(uuid),
      "ip", address.getHostAddress(),
      "address", address.getHostAddress()
    );
  }

  public static PlayerContext empty() {
    return new PlayerContext("", new UUID(0,0), InetAddress.getLoopbackAddress());
  }

  public static PlayerContext of(Player player) {
    return new PlayerContext(player.getName(), player.getUniqueId(), player.getAddress().getAddress());
  }
}
