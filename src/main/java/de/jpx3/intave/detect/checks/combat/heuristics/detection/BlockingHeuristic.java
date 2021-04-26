package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.punishment.AttackCancelType;
import de.jpx3.intave.reflect.ReflectiveDataWatcherAccess;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaPunishmentData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.detect.checks.combat.heuristics.Anomaly.AnomalyOption.*;

public final class BlockingHeuristic extends IntaveMetaCheckPart<Heuristics, BlockingHeuristic.BlockingMeta> {
  private final IntavePlugin plugin;

  public BlockingHeuristic(Heuristics parentCheck) {
    super(parentCheck, BlockingMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "ARM_ANIMATION")
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    BlockingMeta meta = metaOf(user);

    if (event.getPacketType() != PacketType.Play.Client.ARM_ANIMATION) {
      meta.releasedItemAfterClientTick = false;
      meta.ticksBetweenBlockAndUnblock++;
    }
    if (meta.ventosFreundlicherBoolean) {
      meta.clientTicksBetweenBlockingToggle++;
    }
    meta.heldItemOperations = 0;
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_DIG")
    }
  )
  public void receiveInteractionPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaPunishmentData punishmentData = user.meta().punishmentData();
    BlockingMeta meta = metaOf(user);
    PacketContainer packet = event.getPacket();

    if (!user.meta().clientData().flyingPacketStream() || user.meta().abilityData().ignoringMovementPackets()) {
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
          plugin.eventService().attackCancelService().requestDamageCancel(user, AttackCancelType.BLOCKING);
          punishmentData.timeLastBlockCancel = AccessHelper.now();
          Synchronizer.synchronize(() -> ReflectiveDataWatcherAccess.setDataWatcherFlag(player, ReflectiveDataWatcherAccess.DATA_WATCHER_BLOCKING_ID, false));
        }

      }
    } else { // BLOCK_PLACE
      ItemStack itemInHand = packet.getItemModifier().readSafely(0);
      boolean sword = itemInHand != null && itemInHand.getType().name().endsWith("_SWORD");

      if (meta.releasedItemAfterClientTick) {
        String description = "sent multiple blocking interactions between client tick";
        int options = DELAY_128s | SUGGEST_MINING;
        Anomaly anomaly = Anomaly.anomalyOf("141", Confidence.CERTAIN, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(player, anomaly);
        plugin.eventService().attackCancelService().requestDamageCancel(user, AttackCancelType.BLOCKING);
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
            int options = DELAY_128s | SUGGEST_MINING;
            Anomaly anomaly = Anomaly.anomalyOf("142", Confidence.CERTAIN, Anomaly.Type.KILLAURA, description, options);
            parentCheck().saveAnomaly(player, anomaly);
            plugin.eventService().attackCancelService().requestDamageCancel(user, AttackCancelType.BLOCKING);
          }
        } else if (meta.acaBlockingVL > 1) {
          meta.acaBlockingVL -= 2;
        }
      }

      meta.ticksBetweenBlockAndUnblock = 0;
    }
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "HELD_ITEM_SLOT")
    }
  )
  public void receiveHeldItemSlot(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    BlockingMeta blockingMeta = metaOf(player);
    if (user.meta().abilityData().ignoringMovementPackets()) {
      return;
    }
    if (user.meta().clientData().flyingPacketStream() && blockingMeta.heldItemOperations++ >= 1) {
      String description = "sent too many item operations (operations: " + blockingMeta.heldItemOperations + ")";
      Anomaly anomaly = Anomaly.anomalyOf("144", Confidence.NONE, Anomaly.Type.KILLAURA, description, 0);
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  public final static class BlockingMeta extends UserCustomCheckMeta {
    public boolean releasedItemAfterClientTick;
    public int ticksBetweenBlockAndUnblock, clientTicksBetweenBlockingToggle;
    public boolean ventosFreundlicherBoolean;

    public int acaBlockingVL;
    public int heldItemOperations;
  }
}