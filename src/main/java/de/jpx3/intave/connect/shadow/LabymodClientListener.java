package de.jpx3.intave.connect.shadow;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.connect.sibyl.LabyModChannelHelper;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketEventSubscriber;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.reflect.ReflectiveAccess;
import io.netty.buffer.ByteBuf;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.function.BiConsumer;

public final class LabymodClientListener implements PacketEventSubscriber {
  private final static JsonParser jsonParser = new JsonParser();

  private final IntavePlugin plugin;
  private final String channel;
  private final BiConsumer<Player, JsonElement> elementConsumer;

  public LabymodClientListener(IntavePlugin plugin, String channel, BiConsumer<Player, JsonElement> elementConsumer) {
    this.plugin = plugin;
    this.channel = channel;
    this.elementConsumer = elementConsumer;
    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "CUSTOM_PAYLOAD")
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
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        e.printStackTrace();
        tag = "error";
      }
    } else {
      tag = packet.getStrings().getValues().get(0);
    }
    if(tag.startsWith("minecraft:")) {
      tag = tag.substring(10);
    }
    if (!tag.equalsIgnoreCase("LMC")) {
      return;
    }
    ByteBuf bytes = (ByteBuf) packet.getSpecificModifier(ReflectiveAccess.lookupServerClass("PacketDataSerializer")).getValues().get(0);
//    if(bytes.array().length == 0) {
//      return;
//    }
    try {
      bytes.markReaderIndex();
      String messageKey = LabyModChannelHelper.readString(bytes, 32767);
      String messageContent = LabyModChannelHelper.readString(bytes, 32767);
      JsonElement jsonElement = jsonParser.parse(messageContent);
      if(messageKey.equalsIgnoreCase(channel)) {
        elementConsumer.accept(player, jsonElement);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    } finally {
      bytes.resetReaderIndex();
    }
  }
}
