package de.jpx3.intave.connect.sibyl;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.connect.sibyl.auth.SibylAuthentication;
import de.jpx3.intave.connect.sibyl.data.SibylPacketTransmitter;
import de.jpx3.intave.connect.sibyl.data.packet.SibylPacket;
import de.jpx3.intave.connect.sibyl.data.packet.SibylPacketOutAttackCancel;
import de.jpx3.intave.tools.annotate.Native;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class SibylIntegrationService {
  private final IntavePlugin plugin;
  private final SibylAuthentication authentication;
  private final SibylPacketTransmitter packetTransmitter;

  public SibylIntegrationService(IntavePlugin plugin) {
    this.plugin = plugin;
    this.authentication = new SibylAuthentication(plugin);
    this.packetTransmitter = new SibylPacketTransmitter(authentication);
    broadcastRestart();
  }

  @Native
  private void broadcastRestart() {
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      authentication.sendMessageToClient(onlinePlayer, "MC|Brand", "INTAVE", null);
    }
  }

  public void publishAttackCancel(Player attacker, Entity attacked, boolean damage) {
    SibylPacketOutAttackCancel packet = new SibylPacketOutAttackCancel();
    packet.setAttacker(attacker.getUniqueId());
    packet.setAttackedLocation(attacked.getLocation().toVector());
    packet.setDamage(damage);
    broadcastTrustedPacket(packet);
  }

  @Native
  public void broadcastTrustedPacket(SibylPacket packet) {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if(authentication.isAuthenticated(player)) {
        packetTransmitter.transmitPacket(player, packet);
      }
    }
  }

  public SibylPacketTransmitter packetTransmitter() {
    return packetTransmitter;
  }

  public boolean isAuthenticated(Player player) {
    return authentication.isAuthenticated(player);
  }
}
