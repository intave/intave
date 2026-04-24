package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
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
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.meta.PunishmentMetadata;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
  public void receiveMovementAndSwingPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    BlockingMeta meta = metaOf(user);
    MovementMetadata movementData = user.meta().movement();

    if (movementData.lastTeleport == 0) {
      return;
    }

    if (event.getPacketType() != PacketType.Play.Client.ANIMATION) {
      meta.releasedItemAfterClientTick = false;
      meta.ticksBetweenBlockAndUnblock++;
    }
    if (meta.ventosFreundlicherBoolean) {
      meta.clientTicksBetweenBlockingToggle++;
    }
    meta.heldItemOperations = 0;
  }

  @PacketSubscription(
    packetsIn = {
      BLOCK_PLACE, BLOCK_DIG
    }
  )
  public void receiveInteractionPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PunishmentMetadata punishmentData = user.meta().punishment();
    BlockingMeta meta = metaOf(user);

    if (!user.meta().protocol().flyingPacketsAreSent() || user.meta().abilities().ignoringMovementPackets() || user.meta().movement().lastTeleport < 10) {
      return;
    }

    if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
      WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging((PacketReceiveEvent) event);
      if (packet.getAction() == DiggingAction.RELEASE_USE_ITEM) {
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
      WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement((PacketReceiveEvent) event);
      ItemStack itemInHand = packet.getItemStack()
        .map(SpigotConversionUtil::toBukkitItemStack)
        .orElse(null);
      boolean sword = itemInHand != null && itemInHand.getType().name().endsWith("_SWORD");

      if (meta.releasedItemAfterClientTick) {
        String description = "sent multiple blocking interactions per tick (" + (itemInHand == null ? "null" : itemInHand.getType()) + ")";
        Anomaly anomaly = Anomaly.anomalyOf("141", Confidence.NONE, Anomaly.Type.KILLAURA, description);
        parentCheck().saveAnomaly(player, anomaly);
        //dmc7
        user.nerf(AttackNerfStrategy.BLOCKING, "7");
      }

      int clientTicksBetweenBlockingToggle = meta.clientTicksBetweenBlockingToggle;
      int integer = packet.getFaceId();
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
  public void receiveMovementPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    BlockingMeta meta = metaOf(user);
    MovementMetadata movementData = user.meta().movement();
    ProtocolMetadata clientData = user.meta().protocol();
    if (movementData.lastTeleport < 10) {
      return;
    }
    // checks if the client version is above 1.8 for disabling the check if the player is standing still
    if (!movementData.receivedFlyingPacketIn(2) || clientData.protocolVersion() < VER_1_9) {
      if (meta.heldItemOperations > 0) {
        if (meta.blocksPlacedThisTick == 0 || meta.heldItemOperations > 2) {
          String description = "sent too many item operations (operations: " + meta.heldItemOperations + ")";
          description += " (version " + user.meta().protocol().versionString() + ")";
          Anomaly anomaly = Anomaly.anomalyOf("144", Confidence.NONE, Anomaly.Type.KILLAURA, description, 0);
          parentCheck().saveAnomaly(player, anomaly);
//          if(meta.unsendPackets.size() != meta.heldItemOperations) {
//            Bukkit.broadcastMessage("flag " + meta.heldItemOperations + " " + meta.blocksPlacedThisTick);
//          }
        }
      }
    }

    meta.blocksPlacedThisTick = 0;
  }

  @PacketSubscription(
    packetsIn = {
      USE_ITEM
    }
  )
  public void receiveUseItem(ProtocolPacketEvent event) {
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
  public void receiveBlockPlace(ProtocolPacketEvent event) {
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
  public void receiveHeldItemSlot(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    BlockingMeta meta = metaOf(player);
    MovementMetadata movementData = user.meta().movement();
    ProtocolMetadata clientData = user.meta().protocol();
    if (user.meta().abilities().ignoringMovementPackets()) {
      return;
    }

    meta.heldItemOperations++;
  }

  public static final class BlockingMeta extends CheckCustomMetadata {
    private int blocksPlacedThisTick;
    public boolean releasedItemAfterClientTick;
    public int ticksBetweenBlockAndUnblock, clientTicksBetweenBlockingToggle;
    public boolean ventosFreundlicherBoolean;

    public int acaBlockingVL;
    public int heldItemOperations;
  }
}
