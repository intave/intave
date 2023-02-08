package de.jpx3.intave.connect.sibyl;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.connect.sibyl.data.packet.SibylPacketOutMessage;
import de.jpx3.intave.executor.Synchronizer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class SibylMessageTransmitter {
  @Native
  public static void sendMessage(Player player, String message, String... args) {
    if (!Bukkit.isPrimaryThread()) {
      Synchronizer.synchronize(() -> sendMessage(player, message, args));
      return;
    }
    SibylIntegrationService sibyl = IntavePlugin.singletonInstance().sibyl();
    if (sibyl.encryptionActiveFor(player)) {
//      SibylPacketOutMessage packet = new SibylPacketOutMessage();
//      packet.setMessage(String.format(message, (Object[]) args));
//      sibyl.sendTrustedPacket(player, packet);
      player.sendMessage(ChatColor.RED + "(insecure) " + ChatColor.RESET + String.format(message, (Object[]) args));
    } else {
      // for now, just send the message to the player
      player.sendMessage(ChatColor.RED + "(insecure) " + ChatColor.RESET + String.format(message, (Object[]) args));
    }
  }
}
