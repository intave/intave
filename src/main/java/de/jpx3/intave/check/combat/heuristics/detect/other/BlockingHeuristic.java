package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.entity.datawatcher.DataWatcherAccess;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.meta.PunishmentMetadata;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_9;

public final class BlockingHeuristic extends MetaCheckPart<Heuristics, BlockingHeuristic.BlockingMeta> {
  private final IntavePlugin plugin;

  public BlockingHeuristic(Heuristics parentCheck) {
    super(parentCheck, BlockingMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      ARM_ANIMATION, FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void receiveMovementAndSwingPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    BlockingMeta meta = metaOf(user);
    MovementMetadata movementData = user.meta().movement();

    if (movementData.lastTeleport == 0) {
      return;
    }

    if (event.getPacketType() != PacketType.Play.Client.ARM_ANIMATION) {
      meta.releasedItemAfterClientTick = false;
      meta.ticksBetweenBlockAndUnblock++;
    }
    if (meta.ventosFreundlicherBoolean) {
      meta.clientTicksBetweenBlockingToggle++;
    }
    meta.heldItemOperations = 0;
  }

  private void receiveExcludedPacket(Player player, PacketContainer packet) {
    userOf(player).ignoreNextInboundPacket();
    PacketSender.receiveClientPacketFrom(player, packet);
  }

  @PacketSubscription(
    packetsIn = {
      BLOCK_PLACE, BLOCK_DIG
    }
  )
  public void receiveInteractionPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PunishmentMetadata punishmentData = user.meta().punishment();
    BlockingMeta meta = metaOf(user);
    PacketContainer packet = event.getPacket();

    if (!user.meta().protocol().flyingPacketsAreSent() || user.meta().abilities().ignoringMovementPackets()) {
      return;
    }

    if (packet.getType() == PacketType.Play.Client.BLOCK_DIG) {
      EnumWrappers.PlayerDigType playerDigType = packet.getPlayerDigTypes().readSafely(0);
      if (playerDigType == EnumWrappers.PlayerDigType.RELEASE_USE_ITEM) {
        meta.releasedItemAfterClientTick = true;
        meta.ventosFreundlicherBoolean = true;

        int ticksBetweenBlockAndUnblock = meta.ticksBetweenBlockAndUnblock;
        if (ticksBetweenBlockAndUnblock == 0) {
          String description = "unblocked too quickly (" + ticksBetweenBlockAndUnblock + ")";
          int options = DELAY_128s | LIMIT_2 | SUGGEST_MINING;
          Anomaly anomaly = Anomaly.anomalyOf("143", Confidence.MAYBE, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
          //dmc6
          user.nerf(AttackNerfStrategy.BLOCKING, "6");
          punishmentData.timeLastBlockCancel = System.currentTimeMillis();
          Synchronizer.synchronize(() -> DataWatcherAccess.setDataWatcherFlag(player, DataWatcherAccess.WATCHER_BLOCKING_ID, false));
        }

      }
    } else { // BLOCK_PLACE
      ItemStack itemInHand = packet.getItemModifier().readSafely(0);
      boolean sword = itemInHand != null && itemInHand.getType().name().endsWith("_SWORD");

      if (meta.releasedItemAfterClientTick) {
        String description = "sent multiple blocking interactions per tick (" + (itemInHand == null ? "null" : itemInHand.getType()) + ")";
        Anomaly anomaly = Anomaly.anomalyOf("141", Confidence.NONE, Anomaly.Type.KILLAURA, description);
        parentCheck().saveAnomaly(player, anomaly);
        //dmc7
        user.nerf(AttackNerfStrategy.BLOCKING, "7");
      }

      int clientTicksBetweenBlockingToggle = meta.clientTicksBetweenBlockingToggle;
      Integer integer = packet.getIntegers().readSafely(0);
      if (integer == null) {
        integer = 0;
      }
      if (integer == 255 && meta.ventosFreundlicherBoolean && sword) {
        meta.clientTicksBetweenBlockingToggle = 0;
        meta.ventosFreundlicherBoolean = false;

        if (clientTicksBetweenBlockingToggle == 0 && meta.acaBlockingVL < 20) {
          meta.acaBlockingVL++;
          if (meta.acaBlockingVL > 2) {
            String description = "sent too few packets between block-toggle packets (vl: " + meta.acaBlockingVL + ")";
            Anomaly anomaly = Anomaly.anomalyOf("142", Confidence.NONE, Anomaly.Type.KILLAURA, description);
            parentCheck().saveAnomaly(player, anomaly);
            //dmc8
            user.nerf(AttackNerfStrategy.BLOCKING, "8");
          }
        } else if (meta.acaBlockingVL > 1) {
          meta.acaBlockingVL -= 2;
        }
      }

      meta.ticksBetweenBlockAndUnblock = 0;
    }
  }

  //---------other-check-------------

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    BlockingMeta meta = metaOf(user);
    MovementMetadata movementData = user.meta().movement();
    ProtocolMetadata clientData = user.meta().protocol();
    if (movementData.lastTeleport == 0) {
      return;
    }
    // checks if the client version is above 1.8 for disabling the check if the player is standing still
    if (!movementData.recentlyEncounteredFlyingPacket(2) || clientData.protocolVersion() < VER_1_9) {
      if (meta.heldItemOperations > 0) {
        if (meta.blocksPlacedThisTick == 0 || meta.heldItemOperations > 2) {
          String description = "sent too many item operations (operations: " + meta.heldItemOperations + ")";
          description += " (version " + user.meta().protocol().versionString() + ")";
          Anomaly anomaly = Anomaly.anomalyOf("144", Confidence.NONE, Anomaly.Type.KILLAURA, description, 0);
          parentCheck().saveAnomaly(player, anomaly);
//          if(meta.unsendPackets.size() != meta.heldItemOperations) {
//            Bukkit.broadcastMessage("flag " + meta.heldItemOperations + " " + meta.blocksPlacedThisTick);
//          }
          meta.unsendPackets.clear();
        }
      }
    }

//    if(meta.unsendPackets.size() != 0) {
//      PacketContainer packetContainer = meta.unsendPackets.get(0);
//      receiveExcludedPacket(player, packetContainer);
//      meta.unsendPackets.clear();
//    }

    meta.blocksPlacedThisTick = 0;
  }

  @PacketSubscription(
    packetsIn = {
      USE_ITEM
    }
  )
  public void receiveUseItem(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    BlockingMeta meta = metaOf(player);
    // 1.8
    if (clientData.protocolVersion() >= VER_1_9) {
      meta.blocksPlacedThisTick++;
    }
  }

  @PacketSubscription(
    packetsIn = {
      BLOCK_PLACE
    }
  )
  public void receiveBlockPlace(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    BlockingMeta meta = metaOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    // 1.9+
    if (clientData.protocolVersion() < VER_1_9) {
      meta.blocksPlacedThisTick++;
    }
  }

  @PacketSubscription(
    packetsIn = {
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveHeldItemSlot(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    BlockingMeta meta = metaOf(player);
    MovementMetadata movementData = user.meta().movement();
    ProtocolMetadata clientData = user.meta().protocol();
    if (user.meta().abilities().ignoringMovementPackets()) {
      return;
    }

//    if(!movementData.recentlyEncounteredFlyingPacket(2) || clientData.protocolVersion() < VER_1_9) {
//      if (meta.heldItemOperations > 0) {
//        PacketContainer clonedPacket = event.getPacket().deepClone();
//        meta.unsendPackets.add(clonedPacket);
//        event.setCancelled(true);
//      }
//    }

    meta.heldItemOperations++;
  }

  public static final class BlockingMeta extends CheckCustomMetadata {
    private final List<PacketContainer> unsendPackets = new ArrayList<>();
    private int blocksPlacedThisTick;
    public boolean releasedItemAfterClientTick;
    public int ticksBetweenBlockAndUnblock, clientTicksBetweenBlockingToggle;
    public boolean ventosFreundlicherBoolean;

    public int acaBlockingVL;
    public int heldItemOperations;
  }
}