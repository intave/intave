package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.PayloadInReader;
import de.jpx3.intave.packet.view.PacketEventsPayloadInView;
import de.jpx3.intave.packet.view.PayloadInView;
import de.jpx3.intave.packet.view.ProtocolLibPayloadInView;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CUSTOM_PAYLOAD_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.SETTINGS;
import static de.jpx3.intave.user.UserRepository.userOf;

public final class SettingsTracker extends Module {
  @PacketSubscription(
    packetsIn = {
      SETTINGS
    }
  )
  public void receiveClientOptions(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    // On 1.20.2+ the locale is never read off the packet, so it is not touched here either.
    handleClientOptions(
      player,
      MinecraftVersions.VER1_20_2.atOrAbove() ? null : packet.getStrings().read(0)
    );
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      SETTINGS
    }
  )
  public void receiveClientOptions(PacketReceiveEvent event, Player player) {
    if (player == null) {
      return;
    }
    handleClientOptions(
      player,
      MinecraftVersions.VER1_20_2.atOrAbove() ? null : new WrapperPlayClientSettings(event).getLocale()
    );
  }

  /**
   * Engine independent client settings handling.
   *
   * @param locale the locale the packet carries, or null when the server pins it to {@code en_US}.
   */
  private void handleClientOptions(Player player, String locale) {
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    if (MinecraftVersions.VER1_20_2.atOrAbove()) {
      clientData.setLocale("en_US");
      return;
    }
    clientData.setLocale(locale);
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
    if (player == null) {
      return;
    }
    PacketEventsPayloadInView view = PacketEventsPayloadInView.of(event);
    if (view == null) {
      return;
    }
    handlePayloadPacket(player, view);
  }

  /** Engine independent client brand handling; see {@link PayloadInView}. */
  private void handlePayloadPacket(Player player, PayloadInView view) {
    String tag = view.tag();
    if (!tag.equalsIgnoreCase("MC|Brand") && !tag.equalsIgnoreCase("minecraft:brand")) {
      return;
    }
    String brand = view.readStringWithExtraByte();
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    clientData.setClientBrand(brand);
  }
}
