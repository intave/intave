package de.jpx3.intave.check.combat.heuristics.other;

import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.view.BlockPositionView;
import de.jpx3.intave.packet.view.PacketEventsBlockPositionView;
import de.jpx3.intave.packet.view.ProtocolLibBlockPositionView;
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
  public void receiveInteractionPacket(PacketEvent event) {
    handleInteractionPacket(new ProtocolLibBlockPositionView(event));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      BLOCK_DIG
    }
  )
  public void receiveInteractionPacket(PacketReceiveEvent event) {
    PacketEventsBlockPositionView view = PacketEventsBlockPositionView.of(event);
    if (view == null) {
      return;
    }
    handleInteractionPacket(view);
  }

  /**
   * Engine independent handling; see {@link BlockPositionView}. The packet is only read for its dig
   * action, and only cancelled - both of which the view exposes on either engine.
   */
  private void handleInteractionPacket(BlockPositionView view) {
    Player player = view.player();
    if (player == null) {
      // PacketEvents can deliver a packet before the Bukkit player exists.
      view.release();
      return;
    }
    User user = userOf(player);
    CivbreakMeta meta = metaOf(user);
    BlockPositionView.DigAction playerDigType = view.digAction();
    // Note: isMining should set to false on every PlayerDigType except START_DESTROY_BLOCK
//    player.sendMessage("" + playerDigType);
    if (playerDigType == BlockPositionView.DigAction.START_DESTROY_BLOCK) {
      meta.isMining = true;
    }
    if (playerDigType == BlockPositionView.DigAction.STOP_DESTROY_BLOCK) {
      if (user.protocolVersion() < ProtocolMetadata.VER_1_14) {
        if (!meta.isMining) {
//          player.sendMessage("cancel");
          view.setCancelled(true);
        }
      } else {
        // TODO: fix civbreak on 1.14+
        // players don't send a start break packet when destroying a block multiple times on 1.14+
      }
      meta.isMining = false;
    }
    view.release();
  }

  public static final class CivbreakMeta extends CheckCustomMetadata {
    private boolean isMining;
  }
}