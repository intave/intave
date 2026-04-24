package de.jpx3.intave.connect.customclient;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.EventProcessor;
import de.jpx3.intave.connect.sibyl.LabyModChannelHelper;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CUSTOM_PAYLOAD_IN;

public final class CustomClientSupportService implements EventProcessor {
  private static final JsonParser jsonParser = new JsonParser();
  private final IntavePlugin plugin;

  public CustomClientSupportService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void setup() {
    try {
      Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "intave:customclient");
      Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "minecraft:intave");
    } catch (Exception exception) {
//      IntaveLogger.logger().info("Failed to register output channel: " + exception.getClass().getSimpleName());
    }
    Modules.linker().packetEvents().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    packetsIn = {
      CUSTOM_PAYLOAD_IN
    }
  )
  public void receivePayloadPacket(Player player, WrapperPlayClientPluginMessage packet) {
    String tag = packet.getChannelName();
    if (!tag.equalsIgnoreCase("intave")) {
      return;
    }
    ByteBuf bytes = Unpooled.wrappedBuffer(packet.getData());
    try {
      bytes.markReaderIndex();
      String messageKey = LabyModChannelHelper.readString(bytes, 100);
      if (messageKey.equalsIgnoreCase("clientconfig")) {
        User user = UserRepository.userOf(player);
        ConnectionMetadata connectionData = user.meta().connection();
        if (System.currentTimeMillis() - connectionData.lastCCCInfoMessageSent > 4000) {
          IntaveLogger.logger().info(player.getName() + " has sent a custom client configuration (client has special Intave support)");
          connectionData.lastCCCInfoMessageSent = System.currentTimeMillis();
        }
        String messageContent = LabyModChannelHelper.readString(bytes, 32767);
        JsonElement jsonElement = jsonParser.parse(messageContent);
        CustomClientSupportConfig customClientSupportConfig = CustomClientSupportConfig.createFrom(jsonElement);
        user.setCustomClientSupport(customClientSupportConfig);
        sendCustomDataPacket(player, "clientconfig", "received", "minecraft", "intave");
        sendCustomDataPacket(player, "clientconfig", "received", "intave", "customclient");
      }
    } catch (RuntimeException exception) {
      exception.printStackTrace();
      Synchronizer.synchronize(() -> player.kickPlayer("Invalid Intave client support payload packet"));
    } finally {
      bytes.resetReaderIndex();
    }
  }

  private void sendCustomDataPacket(Player player, String channel, String data, String prefix, String key) {
    String packetChannel = MinecraftVersions.VER1_13_0.atOrAbove() ? prefix + ":" + key : prefix + ":" + key;
    byte[] payload = LabyModChannelHelper.getBytesToSend(channel, data);
    WrapperPlayServerPluginMessage packet = new WrapperPlayServerPluginMessage(packetChannel, payload);
    Synchronizer.synchronize(() -> PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet));
  }
}
