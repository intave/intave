package de.jpx3.intave.connect.customclient;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.MinecraftKey;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.connect.sibyl.LabyModChannelHelper;
import de.jpx3.intave.detect.EventProcessor;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.reflect.Lookup;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CUSTOM_PAYLOAD;

public final class CustomClientSupportService implements EventProcessor {
  private final static JsonParser jsonParser = new JsonParser();
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
    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    packetsIn = {
      CUSTOM_PAYLOAD
    }
  )
  public void receivePayloadPacket(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    String tag;
    if (packet.getStrings().getValues().isEmpty()) {
      Object minecraftKey = packet.getMinecraftKeys().getValues().get(0);
      try {
        tag = (String) minecraftKey.getClass().getMethod("toString").invoke(minecraftKey);
      } catch (Exception exception) {
        exception.printStackTrace();
        tag = "error";
      }
    } else {
      tag = packet.getStrings().getValues().get(0);
    }
    if (tag.startsWith("minecraft:")) {
      tag = tag.substring(10);
    }
    if (!tag.equalsIgnoreCase("intave")) {
      return;
    }
    ByteBuf bytes = (ByteBuf) packet.getSpecificModifier(Lookup.serverClass("PacketDataSerializer")).getValues().get(0);
    try {
      bytes.markReaderIndex();
      String messageKey = LabyModChannelHelper.readString(bytes, 100);
      if (messageKey.equalsIgnoreCase("clientconfig")) {
        User user = UserRepository.userOf(player);
        ConnectionMetadata connectionData = user.meta().connection();
        if (AccessHelper.now() - connectionData.lastCCCInfoMessageSent > 4000) {
          IntaveLogger.logger().info(player.getName() + " has sent a custom client configuration (client has special Intave support)");
          connectionData.lastCCCInfoMessageSent = AccessHelper.now();
        }
        String messageContent = LabyModChannelHelper.readString(bytes, 32767);
        JsonElement jsonElement = jsonParser.parse(messageContent);
        CustomClientSupportConfig customClientSupportConfig = CustomClientSupportConfig.createFrom(jsonElement);
        user.setCustomClientSupport(customClientSupportConfig);
        sendCustomDataPacket(player, "clientconfig","received", "minecraft", "intave");
        sendCustomDataPacket(player, "clientconfig","received", "intave", "customclient");
      }
    } catch (RuntimeException exception) {
      exception.printStackTrace();
      Synchronizer.synchronize(() -> player.kickPlayer("Invalid Intave client support payload packet"));
    } finally {
      bytes.resetReaderIndex();
    }
  }

  private void sendCustomDataPacket(Player player, String channel, String data, String prefix, String key) {
    PacketContainer packetContainer = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.CUSTOM_PAYLOAD);
    if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      packetContainer.getMinecraftKeys().write(0, new MinecraftKey(prefix, key));
    } else {
      packetContainer.getStrings().write(0, prefix + ":" + key);
    }
    try {
      //noinspection unchecked
      Class<Object> packetDataSerializerClass = (Class<Object>) Lookup.serverClass("PacketDataSerializer");
      Object packetDataSerializer = packetDataSerializerClass.getConstructor(ByteBuf.class).newInstance(Unpooled.wrappedBuffer(LabyModChannelHelper.getBytesToSend(channel, data)));
      packetContainer.getSpecificModifier(packetDataSerializerClass).write(0, packetDataSerializer);
      Synchronizer.synchronize(() -> {
        try {
          ProtocolLibrary.getProtocolManager().sendServerPacket(player, packetContainer);
        } catch (InvocationTargetException exception) {
          exception.printStackTrace();
        }
      });
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }
}
