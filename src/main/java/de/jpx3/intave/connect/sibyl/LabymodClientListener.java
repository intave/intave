package de.jpx3.intave.connect.sibyl;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.PayloadInReader;
import de.jpx3.intave.packet.view.PacketEventsPayloadInView;
import de.jpx3.intave.packet.view.PayloadInView;
import de.jpx3.intave.packet.view.ProtocolLibPayloadInView;
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
    handlePayloadPacket(player, new ProtocolLibPayloadInView(player, reader));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      CUSTOM_PAYLOAD_IN
    }
  )
  public void receivePayloadPacket(PacketReceiveEvent event, Player player) {
    // PacketEvents can deliver a packet before the Bukkit player exists.
    if (player == null) {
      return;
    }
    PacketEventsPayloadInView view = PacketEventsPayloadInView.of(event);
    if (view == null) {
      return;
    }
    handlePayloadPacket(player, view);
  }

  /** Engine independent LabyMod channel handling; see {@link PayloadInView}. */
  private void handlePayloadPacket(Player player, PayloadInView view) {
    String tag = view.tag();
    if (!tag.equalsIgnoreCase("LMC") && !tag.equalsIgnoreCase("labymod3:main")) {
      return;
    }
    ByteBuf bytes = view.readBytes();
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
