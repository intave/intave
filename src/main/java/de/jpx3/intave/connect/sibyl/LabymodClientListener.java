package de.jpx3.intave.connect.sibyl;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.PayloadInReader;
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
    Modules.linker().packetEvents().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    packetsIn = {
      CUSTOM_PAYLOAD_IN
    }
  )
  public void receivePayloadPacket(Player player, PayloadInReader reader) {
    String tag = reader.tag();
    if (!tag.equalsIgnoreCase("LMC") && !tag.equalsIgnoreCase("labymod3:main")) {
      return;
    }
    ByteBuf bytes = reader.readBytes();
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
