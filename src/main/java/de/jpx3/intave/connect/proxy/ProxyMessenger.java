package de.jpx3.intave.connect.proxy;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.connect.proxy.protocol.IntavePacket;
import de.jpx3.intave.connect.proxy.protocol.IntavePacketSerializer;
import de.jpx3.intave.connect.proxy.protocol.PacketRegister;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.tools.sync.Synchronizer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.spigotmc.SpigotConfig;

import java.util.List;
import java.util.Map;

/**
 * Class generated using IntelliJ IDEA
 * Any distribution is strictly prohibited.
 * Copyright Richard Strunk 2019
 */

/**
 * IPC = Intave Proxy Connection
 */

public final class ProxyMessenger {
  public final static int PROTOCOL_VERSION = 4;
  public final static String INCOMING_CHANNEL = "IPC-P2S";
  public final static String OUTGOING_CHANNEL = "BungeeCord";

  private final IntavePlugin plugin;
  private final IntavePacketSerializer packetSerializer = new IntavePacketSerializer();

  private final boolean packetOutputAllowed;
  private volatile boolean channelOpen;

  private Map<Class<? extends IntavePacket>, List<IntavePacketSubscription>> packetListeners = null;

  public ProxyMessenger(IntavePlugin plugin) {
    this.plugin = plugin;
    boolean spigotExpectingProxyConnections = SpigotConfig.bungee;
    boolean serverInOnlineMode = plugin.getServer().getOnlineMode();
    IntaveLogger logger = plugin.logger();
    this.packetOutputAllowed = plugin.configurationService().configuration().getBoolean("proxy.enable", false);
    if (!packetOutputAllowed) {
      return;
    }
    if (spigotExpectingProxyConnections) {
      if (serverInOnlineMode) {
        logger.info("Spigot expecting proxy connections in online mode?");
        logger.info("Proxy connection offline");
        return;
      } else {
        logger.info("Proxy connection online");
      }
    } else {
      logger.info("Spigot is not in bungee mode");
      logger.info("Proxy connection offline");
      return;
    }
    openChannel();
  }

  private void openChannel() {
    packetListeners = Maps.newHashMap();
    PacketRegister.getPackets()
      .entrySet()
      .stream()
      .filter(integerClassEntry -> integerClassEntry.getKey() >= 100)
      .map(Map.Entry::getValue)
      .forEach(packetType -> packetListeners.put(packetType, Lists.newCopyOnWriteArrayList()));
    Messenger messenger = Bukkit.getServer().getMessenger();
    messenger.registerOutgoingPluginChannel(plugin, OUTGOING_CHANNEL);
    messenger.registerIncomingPluginChannel(plugin, INCOMING_CHANNEL, new IncomingMessageListener(plugin, this));
    channelOpen = true;
  }

  public void closeChannel() {
    if(packetListeners != null) {
      packetListeners.clear();
    }
    Messenger messenger = Bukkit.getServer().getMessenger();
    messenger.unregisterIncomingPluginChannel(plugin);
    messenger.unregisterOutgoingPluginChannel(plugin);
    channelOpen = false;
  }

  public void sendPacket(Player player, IntavePacket packet) {
    if (!isChannelOpen() || !packetOutputAllowed) {
      return;
    }
    BackgroundExecutor.execute(() -> {
      ByteArrayDataOutput byteOutput = ByteStreams.newDataOutput();
      byteOutput.writeUTF("IPC_BEGIN");
      byteOutput.writeInt(PROTOCOL_VERSION);
      byteOutput.writeInt(PacketRegister.getIdentifierOf(packet.getClass()));
      byteOutput.write(packetSerializer.serializeDataFrom(packet));
      byteOutput.writeUTF("IPC_END");
      Synchronizer.synchronize(() -> player.sendPluginMessage(plugin, OUTGOING_CHANNEL, byteOutput.toByteArray()));
    });
  }

  public <T extends IntavePacket> void subscribe(Class<T> type, IntavePacketSubscription<T> interpreter) {
    if (!isChannelOpen()) {
      return;
    }
    packetListeners.get(type).add(interpreter);
  }

  public boolean isChannelOpen() {
    return channelOpen;
  }

  public Map<Class<? extends IntavePacket>, List<IntavePacketSubscription>> packetSubscriptions() {
    return packetListeners;
  }
}
