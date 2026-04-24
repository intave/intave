package de.jpx3.intave.check.other.inventoryclickanalysis;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.InventoryClickAnalysis;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class PacketDelayAnalyzer extends MetaCheckPart<InventoryClickAnalysis, PacketDelayAnalyzer.TimingData> {
  public PacketDelayAnalyzer(InventoryClickAnalysis parentCheck) {
    super(parentCheck, TimingData.class);
  }

  @PacketSubscription(
    packetsIn = {WINDOW_CLICK}
  )
  public void receiveInventoryClick(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    TimingData meta = metaOf(player);
    long difference = System.currentTimeMillis() - meta.lastMovementTimestamps;
    double averageMovementPacketTimestamp = user.meta().connection().averageMovementPacketTimestamp();

    if (difference < 15 && Math.abs(averageMovementPacketTimestamp - 50) < 10) {
      String message = ChatColor.RED + "[InvAnalysis] " + player.getName() + " is clicking suspiciously on items: "
        + difference + " pd, " + averageMovementPacketTimestamp + " md";
//      Synchronizer.synchronize(() -> processSibylDebug(message));
//      SibylBroadcast.broadcast(message);
    }
  }

//  @Native
//  private void processSibylDebug(String message) {
//    IntavePlugin plugin = IntavePlugin.singletonInstance();
//    for (Player onlinePlayer : MessageChannelSubscriptions.sibylReceiver()/*Bukkit.getOnlinePlayers()*/) {
//      if (plugin.sibylIntegrationService().isAuthenticated(onlinePlayer)) {
//        onlinePlayer.sendMessage(message);
//      }
//    }
//  }

  @PacketSubscription(
    packetsIn = {FLYING, POSITION, LOOK, POSITION_LOOK}
  )
  public void receiveMovement(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    metaOf(player).lastMovementTimestamps = System.currentTimeMillis();
  }

  public static final class TimingData extends CheckCustomMetadata {
    public int threshold;
    public long lastMovementTimestamps;
  }
}