package de.jpx3.intave.module.warning;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PayloadInReader;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CUSTOM_PAYLOAD_IN;

public final class ClientWarningModule extends Module {
  private final IntavePlugin plugin;
  private final ClientDataList clientDataList = ClientDataList.generate();
  private final Map<UUID, Long> lastInformationPrinted = GarbageCollector.watch(new HashMap<>());

  public ClientWarningModule(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @PacketSubscription(
    packetsIn = {
      CUSTOM_PAYLOAD_IN
    }
  )
  public void receivePayloadPacket(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();

    PayloadInReader reader = PacketReaders.readerOf(packet);
    String tag = reader.tag();

    if (tag.endsWith("Brand")) {
      String brand = reader.readStringWithExtraByte();
      ClientData clientData = clientDataOfBrand(brand);
      if (clientData != null) {
        Synchronizer.synchronize(() -> warn(player, clientData));
      }
    } else {
      ClientData clientData = clientDataOfPayload(tag);
      if (clientData != null) {
        Synchronizer.synchronize(() -> warn(player, clientData));
      }
    }
    reader.release();
  }

  private void warn(Player player, ClientData clientData) {
    Long lastInformation = lastInformationPrinted.computeIfAbsent(player.getUniqueId(), uuid -> 0L);
    if (System.currentTimeMillis() - lastInformation < 1000) {
      return;
    }
    lastInformationPrinted.put(player.getUniqueId(), System.currentTimeMillis());
    UserRepository.userOf(player).meta().protocol().setClientBrand(clientData.name());
    switch (clientData.action()) {
      case "message":
        Synchronizer.synchronize(() -> {
          player.sendMessage("");
          player.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "WARNING " + ChatColor.RED + clientData.content());
          player.sendMessage("");
        });
        break;
      case "kick":
        Synchronizer.synchronize(() -> player.kickPlayer(ChatColor.RED + clientData.content()));
        break;
    }
  }

  private ClientData clientDataOfPayload(String payload) {
    if (payload.equalsIgnoreCase("ignore")) {
      return null;
    }
    return clientDataList
      .content()
      .stream()
      .filter(clientData -> clientData.payload().equalsIgnoreCase(payload))
      .findFirst()
      .orElse(null);
  }

  private ClientData clientDataOfBrand(String brand) {
    if (brand.equalsIgnoreCase("ignore")) {
      return null;
    }
    return clientDataList
      .content()
      .stream()
      .filter(clientData -> brand.toLowerCase(Locale.ROOT).contains(clientData.brandcont().toLowerCase(Locale.ROOT)))
      .findFirst()
      .orElse(null);
  }
}
