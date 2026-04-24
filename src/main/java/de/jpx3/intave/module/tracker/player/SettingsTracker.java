package de.jpx3.intave.module.tracker.player;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CUSTOM_PAYLOAD_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.SETTINGS;
import static de.jpx3.intave.user.UserRepository.userOf;

public final class SettingsTracker extends Module {
  @PacketSubscription(
    packetsIn = {
      SETTINGS
    }
  )
  public void receiveClientOptions(Player player, WrapperPlayClientSettings packet) {
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    if (MinecraftVersions.VER1_20_2.atOrAbove()) {
      clientData.setLocale("en_US");
      return;
    }
    String locale = packet.getLocale();
    clientData.setLocale(locale);
  }

  @PacketSubscription(
    packetsIn = {
      CUSTOM_PAYLOAD_IN
    }
  )
  public void receivePayloadPacket(Player player, WrapperPlayClientPluginMessage packet) {
    String tag = packet.getChannelName();
    if (!tag.equalsIgnoreCase("MC|Brand") && !tag.equalsIgnoreCase("minecraft:brand")) {
      return;
    }
    String brand = decodeBrand(packet.getData());
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    clientData.setClientBrand(brand);
  }

  private String decodeBrand(byte[] data) {
    if (data == null || data.length == 0) {
      return "";
    }
    int[] offset = {0};
    int length = readVarInt(data, offset);
    if (length < 0 || offset[0] + length > data.length) {
      return new String(data, StandardCharsets.UTF_8);
    }
    return new String(data, offset[0], length, StandardCharsets.UTF_8);
  }

  private int readVarInt(byte[] data, int[] offset) {
    int value = 0;
    int position = 0;
    while (offset[0] < data.length && position < 35) {
      int currentByte = data[offset[0]++] & 0xFF;
      value |= (currentByte & 0x7F) << position;
      if ((currentByte & 0x80) == 0) {
        return value;
      }
      position += 7;
    }
    return -1;
  }
}
