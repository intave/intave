package de.jpx3.intave.connect.proxy;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.connect.proxy.protocol.packets.IntavePacketInRequestVersion;
import de.jpx3.intave.connect.proxy.protocol.packets.IntavePacketOutVersion;

/**
 * Class generated using IntelliJ IDEA
 * Any distribution is strictly prohibited.
 * Copyright Richard Strunk 2019
 */

public final class ProxyListeningResponder {
  private final IntavePlugin plugin;

  public ProxyListeningResponder(IntavePlugin plugin) {
    this.plugin = plugin;
    setupPacketResponses();
  }

  private void setupPacketResponses() {
    plugin.proxy().subscribe(IntavePacketInRequestVersion.class, (sender, packet) ->
      plugin.proxy().sendPacket(sender, new IntavePacketOutVersion(IntavePlugin.fullVersion(), ProxyMessenger.PROTOCOL_VERSION)));
  }
}
