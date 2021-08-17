package de.jpx3.intave.detect.checks.other.inventoryclickanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.detect.MetaCheckPart;
import de.jpx3.intave.detect.checks.other.InventoryClickAnalysis;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.user.MessageChannelSubscriptions;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class InventoryClickPacketDelayAnalyzer extends MetaCheckPart<InventoryClickAnalysis, InventoryClickPacketDelayAnalyzer.TimingData> {
  public InventoryClickPacketDelayAnalyzer(InventoryClickAnalysis parentCheck) {
    super(parentCheck, TimingData.class);
  }

  @PacketSubscription(
    packetsIn = {WINDOW_CLICK}
  )
  public void receiveInventoryClick(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    TimingData meta = metaOf(player);
    long difference = AccessHelper.now() - meta.lastMovementTimestamps;
    double averageMovementPacketTimestamp = user.meta().connection().averageMovementPacketTimestamp();

    if (difference < 15 && Math.abs(averageMovementPacketTimestamp - 50) < 10) {
      String message = ChatColor.RED + "[InvAnalysis] " + player.getName() + " is clicking suspiciously on items: "
        + difference + " pd, " + averageMovementPacketTimestamp + " md";
      Synchronizer.synchronize(() -> processSibylDebug(message));
    }
  }

  @Native
  private void processSibylDebug(String message) {
    IntavePlugin plugin = IntavePlugin.singletonInstance();
    for (Player onlinePlayer : MessageChannelSubscriptions.sibylReceiver()/*Bukkit.getOnlinePlayers()*/) {
      if (plugin.sibylIntegrationService().isAuthenticated(onlinePlayer)) {
        onlinePlayer.sendMessage(message);
      }
    }
  }

  @PacketSubscription(
    packetsIn = {FLYING, POSITION, LOOK, POSITION_LOOK}
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    metaOf(player).lastMovementTimestamps = AccessHelper.now();
  }

  public static final class TimingData extends CheckCustomMetadata {
    public int threshold;
    public long lastMovementTimestamps;
  }
}