package de.jpx3.intave.connect.proxy;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.connect.proxy.protocol.IntavePacket;
import de.jpx3.intave.connect.proxy.protocol.IntavePacketDeserializer;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import static de.jpx3.intave.connect.proxy.ProxyMessenger.INCOMING_CHANNEL;
import static de.jpx3.intave.connect.proxy.ProxyMessenger.PROTOCOL_VERSION;

/**
 * Class generated using IntelliJ IDEA
 * Any distribution is strictly prohibited.
 * Copyright Richard Strunk 2019
 */

public final class IncomingMessageListener implements PluginMessageListener {

  private final IntavePlugin plugin;
  private final ProxyMessenger proxyMessenger;
  private final IntavePacketDeserializer packetDeserializer = new IntavePacketDeserializer();

  IncomingMessageListener(IntavePlugin plugin, ProxyMessenger proxyMessenger) {
    this.plugin = plugin;
    this.proxyMessenger = proxyMessenger;
  }

  @Override
  public void onPluginMessageReceived(String channelName, Player player, byte[] bytes) {
    if (!channelName.equals(INCOMING_CHANNEL)) {
      return;
    }
    ByteArrayDataInput inputByteBuf = ByteStreams.newDataInput(bytes);
    try {
      BackgroundExecutor.execute(() -> {
        String subChannel = inputByteBuf.readUTF();
        if (!subChannel.equalsIgnoreCase("IPC_BEGIN")) {
          return;
        }
        try {
          int protocolVersion = inputByteBuf.readInt();
          if (protocolVersion < PROTOCOL_VERSION) {
            plugin.logger().error("Warning: You are using an outdated version of IntaveProxySupport. This might cause issues");
          }
          int packetId = inputByteBuf.readInt();
          IntavePacket constructedPacket = packetDeserializer.deserializeUsing(packetId, inputByteBuf);
          Class<? extends IntavePacket> packetClass = constructedPacket.getClass();
          //noinspection unchecked
          proxyMessenger.packetSubscriptions().get(packetClass).forEach(intavePacketListener ->
            intavePacketListener.onIncomingPacket(player, constructedPacket));
          try {
            if (!inputByteBuf.readUTF().equalsIgnoreCase("IPC_END")) {
              throw new RuntimeException();
            }
          } catch (RuntimeException ignored) {
            plugin.logger().error("Incoming packet corrupted");
          }
        } catch (Exception exception) {
          exception.printStackTrace();
          User user = UserRepository.userOf(player);
          user.synchronizedDisconnect("Something went wrong processing an incoming packet");
        }
      });
    } catch (Exception exception) {
      player.kickPlayer(exception.getMessage());
    }
  }
}