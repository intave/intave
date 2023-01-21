package de.jpx3.intave.connect.sibyl;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import io.netty.buffer.ByteBuf;
import org.bukkit.entity.Player;

import java.util.function.BiConsumer;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CUSTOM_PAYLOAD_IN;

public final class LabymodClientListener implements PacketEventSubscriber {
  private static final JsonParser jsonParser = new JsonParser();

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
    packetsIn = {
      CUSTOM_PAYLOAD_IN
    }
  )
  public void receivePayloadPacket(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    String tag;
    if (packet.getStrings().getValues().isEmpty()) {
      Object minecraftKey = packet.getMinecraftKeys().getValues().get(0);
      try {
        tag = (String) minecraftKey.getClass().getMethod("getFullKey").invoke(minecraftKey);
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
    if (!tag.equalsIgnoreCase("LMC") && !tag.equalsIgnoreCase("labymod3:main")) {
      return;
    }
    ByteBuf bytes = (ByteBuf) packet.getSpecificModifier(Lookup.serverClass("PacketDataSerializer")).getValues().get(0);
    try {
      bytes.markReaderIndex();
      String messageKey = LabyModChannelHelper.readString(bytes, Short.MAX_VALUE);
      if (messageKey.equalsIgnoreCase(channel)) {
        String messageContent = LabyModChannelHelper.readString(bytes, Short.MAX_VALUE);
        JsonElement jsonElement = jsonParser.parse(messageContent);
        elementConsumer.accept(player, jsonElement);
      }
    } catch (Exception exception) {
      exception.printStackTrace();
    } finally {
      bytes.resetReaderIndex();
    }
  }
}
