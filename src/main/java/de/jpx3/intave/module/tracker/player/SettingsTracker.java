package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.PayloadInReader;
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
  public void receiveClientOptions(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketContainer packet = event.getPacket();
    ProtocolMetadata clientData = user.meta().protocol();
    if (MinecraftVersions.VER1_20_2.atOrAbove()) {
      clientData.setLocale("en_US");
      return;
    }
    String locale = packet.getStrings().read(0);
    clientData.setLocale(locale);
  }

  @PacketSubscription(
    packetsIn = {
      CUSTOM_PAYLOAD_IN
    }
  )
  public void receivePayloadPacket(Player player, PayloadInReader reader) {
    String tag = reader.tag();
    if (!tag.equalsIgnoreCase("MC|Brand") && !tag.equalsIgnoreCase("minecraft:brand")) {
      return;
    }
    String brand = reader.readStringWithExtraByte();
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    clientData.setClientBrand(brand);
  }
}
