package de.jpx3.intave.connect.sibyl.data;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.jpx3.intave.connect.sibyl.LabyModChannelHelper;
import de.jpx3.intave.connect.sibyl.SibylIntegrationService;
import de.jpx3.intave.connect.sibyl.auth.SibylAuthentication;
import de.jpx3.intave.connect.sibyl.data.packet.SibylPacket;
import de.jpx3.intave.executor.Synchronizer;
import org.bukkit.entity.Player;

import javax.crypto.Cipher;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class SibylPacketTransmitter {
  private final SibylAuthentication authentication;
  private final SibylIntegrationService service;

  private final ThreadLocal<Cipher> aesCiphers = ThreadLocal.withInitial(() -> {
    try {
      return Cipher.getInstance("AES");
    } catch (Exception exception) {
      exception.printStackTrace();
      return null;
    }
  });

  public SibylPacketTransmitter(SibylAuthentication authentication, SibylIntegrationService service) {
    this.authentication = authentication;
    this.service = service;
  }

  public void transmitPacket(Player player, SibylPacket sibylPacket) {
    String packetName = sibylPacket.packetName();
    JsonObject packetContent = new JsonObject();
    if (service.encryptionActiveFor(player)) {
      try {
        String text = sibylPacket.asJsonElement().toString();
        byte[] textBytes = text.getBytes(UTF_8);
        Cipher aes = aesCiphers.get();
        aes.init(Cipher.ENCRYPT_MODE, service.keyOf(player));
        byte[] encryptedText = aes.doFinal(textBytes);
        packetContent.addProperty("name", packetName); // maybe encrypt this too?
        packetContent.addProperty("content", Base64.getEncoder().encodeToString(encryptedText));
      } catch (Exception exception) {
        exception.printStackTrace();
      }
    } else {
      packetContent.addProperty("name", packetName);
      packetContent.add("content", sibylPacket.asJsonElement());
    }
    transmitPacketDataToPlayer(player, packetContent);
  }

  private void transmitPacketDataToPlayer(Player player, JsonElement jsonElement) {
    if (!authenticated(player)) {
      return;
    }
    byte[] bytesToSend = LabyModChannelHelper.getBytesToSend("sibyl-data-s2c", jsonElement == null ? null : jsonElement.toString());
    WrapperPlayServerPluginMessage packet = new WrapperPlayServerPluginMessage("labymod3:main", bytesToSend);
    Synchronizer.synchronize(() -> PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet));
  }

  private boolean authenticated(Player player) {
    return authentication.isAuthenticated(player);
  }
}
