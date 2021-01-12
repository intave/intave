package de.jpx3.intave.tools.placeholder;

import com.google.common.collect.ImmutableMap;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;

public final class ConnectionContext extends PlaceholderContext {

  private final String playerName;
  private final UUID uuid;
  private final InetAddress address;

  public ConnectionContext(String playerName, UUID uuid, InetAddress address) {
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
}
