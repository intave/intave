package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

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
    User user = userOf(player);
    PacketContainer packet = event.getPacket();
    ProtocolMetadata clientData = user.meta().protocol();
    String locale = packet.getStrings().read(0);
    clientData.setLocale(locale);
  }
}
