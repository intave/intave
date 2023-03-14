package de.jpx3.intave.connect.sibyl.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.connect.sibyl.LabymodClientListener;
import de.jpx3.intave.connect.sibyl.SibylIntegrationService;
import de.jpx3.intave.connect.sibyl.data.packet.SibylPacket;
import de.jpx3.intave.connect.sibyl.data.packet.SibylPacketInConfirmEncryption;
import de.jpx3.intave.connect.sibyl.data.packet.SibylPacketRegister;
import org.bukkit.entity.Player;

import javax.crypto.Cipher;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class SibylPacketReceiver {
  private final SibylIntegrationService service;
  private final LabymodClientListener dataListener;

//  private final Map<UUID, Cipher> decryptionCiphers = GarbageCollector.watch(new HashMap<>());

  private final ThreadLocal<Cipher> aesCiphers = ThreadLocal.withInitial(() -> {
    try {
      return Cipher.getInstance("AES");
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  });

  public SibylPacketReceiver(IntavePlugin plugin, SibylIntegrationService service) {
    this.dataListener = new LabymodClientListener(plugin, "sibyl-data-c2s", this::receiveData);
    this.service = service;
  }

  @Native
  public void receiveData(Player player, JsonElement element) {
    if (!service.isAuthenticated(player)) {
      return;
    }
    try {
      JsonObject packetData = element.getAsJsonObject();
      String packetName = packetData.get("name").getAsString();
      JsonElement packetContent = packetData.get("content");

      if (service.encryptionActiveFor(player)) {
        String base64encryptedText = packetContent.getAsString();
        byte[] encryptedText = Base64.getDecoder().decode(base64encryptedText);
        Cipher aes = aesCiphers.get();
        aes.init(Cipher.DECRYPT_MODE, service.keyOf(player));
        byte[] decryptedText = aes.doFinal(encryptedText);
        packetContent = new JsonParser().parse(new String(decryptedText, UTF_8));
      }

      SibylPacket sibylPacket = SibylPacketRegister.createFrom(packetName, packetContent);

      // find a better way to do this
      if (sibylPacket instanceof SibylPacketInConfirmEncryption) {
        service.confirmEncryption(player, (SibylPacketInConfirmEncryption) sibylPacket);
      }
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }
}
