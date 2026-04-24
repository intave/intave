package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;

public final class CivbreakHeuristic extends MetaCheckPart<Heuristics, CivbreakHeuristic.CivbreakMeta> {

  public CivbreakHeuristic(Heuristics parentCheck) {
    super(parentCheck, CivbreakMeta.class);
  }

  /*
  What is civbreak?
  Civbreak abuses a server bug where you can instant break a block on a block position where you
  already destroyed one block. So civbreak only sends multiple STOP_DESTROY_BLOCK packets after the
  player destroyed the block once.
   */
  @PacketSubscription(
    packetsIn = {
      BLOCK_DIG
    }
  )
  public void receiveInteractionPacket(ProtocolPacketEvent event, WrapperPlayClientPlayerDigging packet) {
    Player player = event.getPlayer();
    User user = userOf(player);
    CivbreakMeta meta = metaOf(user);
    DiggingAction playerDigType = packet.getAction();
    // Note: isMining should set to false on every PlayerDigType except START_DESTROY_BLOCK
//    player.sendMessage("" + playerDigType);
    if (playerDigType == DiggingAction.START_DIGGING) {
      meta.isMining = true;
    }
    if (playerDigType == DiggingAction.FINISHED_DIGGING) {
      if (user.protocolVersion() < ProtocolMetadata.VER_1_14) {
        if (!meta.isMining) {
//          player.sendMessage("cancel");
          event.setCancelled(true);
        }
      } else {
        // TODO: fix civbreak on 1.14+
        // players don't send a start break packet when destroying a block multiple times on 1.14+
      }
      meta.isMining = false;
    }
  }

  public static final class CivbreakMeta extends CheckCustomMetadata {
    private boolean isMining;
  }
}
