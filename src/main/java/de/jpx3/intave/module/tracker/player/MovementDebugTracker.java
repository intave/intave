package de.jpx3.intave.module.tracker.player;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateCommandBlockMinecart;
import de.jpx3.intave.connect.sibyl.LabyModChannelHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.github.retrooper.packetevents.event.CancellableEvent;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Base64;
import java.util.Map;

import static de.jpx3.intave.IntaveControl.ENABLE_MOVEMENT_DEBUGGER_COLLECTOR;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.CUSTOM_PAYLOAD_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.SET_COMMAND_MINECART;

public final class MovementDebugTracker extends Module implements PluginMessageListener {

  @Override
  public void enable() {
    Messenger messenger = Bukkit.getMessenger();
    messenger.registerIncomingPluginChannel(plugin, "intave:movdebug", this);
  }

  @Override
  public void onPluginMessageReceived(String s, Player player, byte[] bytes) {
    if (!ENABLE_MOVEMENT_DEBUGGER_COLLECTOR) {
      return;
    }
    User user = UserRepository.userOf(player);
    if (s.equals("intave:movdebug")) {
      Map<String, Double> movementDebugValues = user.meta().movement().clientMovementDebugValues;
      ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes);
      int length = byteBuf.readInt();
      if (length > 100 || length < 0) {
        user.kick("Too many debug parameters " + length);
      }

      int maxReads = 10;
      while (length > 0 && maxReads-- > 0) {
        int nameLength = byteBuf.readInt();

        if (nameLength > 100 || nameLength < 0) {
          user.kick("Invalid movement debug name length: " + nameLength);
        }

        // read chars
        char[] chars = new char[nameLength];
        for (int i = 0; i < nameLength; i++) {
          chars[i] = byteBuf.readChar();
        }
        String name = new String(chars);
        double value = byteBuf.readDouble();
        movementDebugValues.put(name, value);
        length--;
      }
    }
  }

  private final static String PREFIX = "$intave/";

  @PacketSubscription(
    packetsIn = {CUSTOM_PAYLOAD_IN}
  )
  public void onCustomPayloadIn(
    User user, WrapperPlayClientPluginMessage packet,
    CancellableEvent cancellableEvent
  ) {
    if (!ENABLE_MOVEMENT_DEBUGGER_COLLECTOR) {
      return;
    }
    try {
      if (packet.getChannelName().equalsIgnoreCase("MC|AdvCdm")) {
        ByteBuf bytes = Unpooled.wrappedBuffer(packet.getData());
        int type = bytes.readByte();
        if (type != 1) {
          return;
        }
        int entityId = bytes.readInt();
        if (entityId != -1) {
          return;
        }
        String command = LabyModChannelHelper.readString(bytes, bytes.readableBytes());
        String subCommand = command.substring(PREFIX.length());
        String[] split = subCommand.split(":");
        if (split.length != 2) {
          System.out.println("Invalid command format: " + command);
          cancellableEvent.setCancelled(true);
          return;
        }
        String key = split[0];
        String value = split[1];
        ProtocolMetadata protocol = user.meta().protocol();
        protocol.debugStates.put(key, value);
        cancellableEvent.setCancelled(true);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @PacketSubscription(
    packetsIn = {SET_COMMAND_MINECART},
    debug = true
  )
  public void onTabCompleteIn(
    User user, WrapperPlayClientUpdateCommandBlockMinecart packet,
    CancellableEvent cancellableEvent
  ) {
    if (!ENABLE_MOVEMENT_DEBUGGER_COLLECTOR) {
      return;
    }
    int id = packet.getEntityId();
    if (id != -1) {
      return;
    }
    String command = packet.getCommand();
    if (command == null) {
      return;
    }
    if (!command.startsWith(PREFIX)) {
      return;
    }
    String subCommand = command.substring(PREFIX.length());
    String[] split = subCommand.split(":");
    if (split.length != 2) {
      cancellableEvent.setCancelled(true);
      return;
    }
    String key = split[0];
    String value = split[1];
    ProtocolMetadata protocol = user.meta().protocol();
    protocol.debugStates.put(key, value);
    cancellableEvent.setCancelled(true);

    if (key.equalsIgnoreCase("entity_pos_after_update")) {
      if (value != null) {
        byte[] bytes = Base64.getDecoder().decode(value);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes);
        int entityId = byteBuf.readInt();
        double posX = byteBuf.readDouble();
        double posY = byteBuf.readDouble();
        double posZ = byteBuf.readDouble();
        Position toldPosition = new Position(posX, posY, posZ);
//        System.out.println(toldPosition.toString());
        if (entityId == protocol.lastEntityId) {
          protocol.lastEntityId = 0;
          Position lastEntityPosition = protocol.lastEntityPosition;
          //          if (entityName().toLowerCase().contains("chicken")) {
//          System.out.println("B " + (System.currentTimeMillis() % 1000) + " " + "/" + entityId + " " + toldPosition);
//          }
//          user.player().sendMessage("Distance: " + toldPosition.distance(lastEntityPosition));
        }
      }
    }
  }
}
