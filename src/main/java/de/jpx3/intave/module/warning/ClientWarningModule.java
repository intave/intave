package de.jpx3.intave.module.warning;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.google.common.base.Charsets;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.reflect.Lookup;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.GarbageCollector;
import io.netty.buffer.ByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CUSTOM_PAYLOAD;

public final class ClientWarningModule extends Module {
  private final IntavePlugin plugin;
  private final ClientDataList clientDataList = ClientDataList.generate();
  private final Map<UUID, Long> lastInformationPrinted = GarbageCollector.watch(new HashMap<>());

  public ClientWarningModule(IntavePlugin plugin) {
    this.plugin = plugin;
  }


  @PacketSubscription(
    packetsIn = {
      CUSTOM_PAYLOAD
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
    if (tag.endsWith("Brand")) {
      ByteBuf bytes = (ByteBuf) packet.getSpecificModifier(Lookup.serverClass("PacketDataSerializer")).getValues().get(0);
      try {
        bytes.markReaderIndex();
        int length = bytes.readByte();
        String brandString = bytes.toString(Charsets.UTF_8);
        ClientData clientData = clientDataOfBrand(brandString);
        if (clientData != null) {
          Synchronizer.synchronize(() -> warn(player, clientData));
        }
      } catch (Exception exception) {
        exception.printStackTrace();
      }
    } else {
      ClientData clientData = clientDataOfPayload(tag);
      if (clientData != null) {
        Synchronizer.synchronize(() -> warn(player, clientData));
      }
    }
  }

  @Native
  private void warn(Player player, ClientData clientData) {
    Long lastInformation = lastInformationPrinted.computeIfAbsent(player.getUniqueId(), uuid -> 0L);
    if (AccessHelper.now() - lastInformation < 1000) {
      return;
    }
    lastInformationPrinted.put(player.getUniqueId(), AccessHelper.now());

    String message = ChatColor.RED + "[CW] " + player.getName() + " joined with " + clientData.name();
    for (Player authenticatedPlayer : Bukkit.getOnlinePlayers()) {
      if (plugin.sibylIntegrationService().isAuthenticated(authenticatedPlayer)) {
        authenticatedPlayer.sendMessage(message);
      }
    }
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
