/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.plib.reader.PayloadInReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.packet.contract.PacketType.Client.CUSTOM_PAYLOAD_IN;
import static de.jpx3.intave.packet.contract.PacketType.Client.SETTINGS;
import static de.jpx3.intave.user.UserRepository.userOf;

public final class SettingsTracker extends Module {
  @PacketSubscription(
    packetsIn = {
      SETTINGS
    }
  )
  public void receiveClientOptions(PacketEvent event) {
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
