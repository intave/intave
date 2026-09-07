package de.jpx3.intave.check.other.inventoryclickanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.InventoryClickAnalysis;
import de.jpx3.intave.module.linker.packet.Engine;
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
  public void receiveInventoryClick(PacketEvent event) {
    handleInventoryClick(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {WINDOW_CLICK}
  )
  public void receiveInventoryClick(Player player) {
    handleInventoryClick(player);
  }

  /** Engine independent body; the subscription only ever needed the player. */
  private void handleInventoryClick(Player player) {
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
  public void receiveMovement(PacketEvent event) {
    metaOf(event.getPlayer()).lastMovementTimestamps = System.currentTimeMillis();
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {FLYING, POSITION, LOOK, POSITION_LOOK}
  )
  public void receiveMovement(Player player) {
    metaOf(player).lastMovementTimestamps = System.currentTimeMillis();
  }

  public static final class TimingData extends CheckCustomMetadata {
    public int threshold;
    public long lastMovementTimestamps;
  }
}