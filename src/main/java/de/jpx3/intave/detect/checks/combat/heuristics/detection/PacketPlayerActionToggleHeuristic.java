package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
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
import de.jpx3.intave.tools.packet.PlayerAction;
import de.jpx3.intave.tools.packet.PlayerActionResolver;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.*;
import org.bukkit.entity.Player;

import static de.jpx3.intave.reflect.ReflectiveDataWatcherAccess.DATA_WATCHER_SNEAK_ID;

public final class PacketPlayerActionToggleHeuristic extends IntaveMetaCheckPart<Heuristics, PacketPlayerActionToggleHeuristic.PacketSprintToggleHeuristicMeta> {
  private final IntavePlugin plugin;

  public PacketPlayerActionToggleHeuristic(Heuristics parentCheck) {
    super(parentCheck, PacketSprintToggleHeuristicMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK")
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    PacketSprintToggleHeuristicMeta heuristicMeta = metaOf(player);
    heuristicMeta.reset();
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "ENTITY_ACTION")
    }
  )
  public void receiveEntityAction(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaAbilityData abilityData = meta.abilityData();
    UserMetaClientData clientData = meta.clientData();
    UserMetaPunishmentData punishmentData = user.meta().punishmentData();
    PacketSprintToggleHeuristicMeta heuristicMeta = metaOf(user);

    PacketContainer packet = event.getPacket();
    PlayerAction action = PlayerActionResolver.resolveActionFromPacket(packet);

    boolean sprint = action == PlayerAction.START_SPRINTING || action == PlayerAction.STOP_SPRINTING;
    boolean sneak = action == PlayerAction.START_SNEAKING || action == PlayerAction.STOP_SNEAKING;
    if (!sprint && !sneak) {
      return;
    }

    if (abilityData.ignoringMovementPackets()) {
      heuristicMeta.reset();
      return;
    }

    boolean flag = sprint
      ? heuristicMeta.sprintTogglesInTick++ >= 1
      : heuristicMeta.sneakTogglesInTick++ >= 1;

    if (flag) {
      boolean flyingPacketStream = clientData.flyingPacketStream();
      boolean checkable = flyingPacketStream || !movementData.recentlyEncounteredFlyingPacket(20);
      if (checkable) {
        String description = sprint
          ? "sent too many sprint toggles per tick (" + heuristicMeta.sprintTogglesInTick + ")"
          : "sent too many sneak toggles per tick (" + heuristicMeta.sneakTogglesInTick + ")" ;
        if (!flyingPacketStream) {
          description += " (last flying: " + movementData.pastFlyingPacketAccurate() + ")";
        }
        // could be CERTAIN on 1.8
        Confidence confidence = flyingPacketStream ? Confidence.PROBABLE : Confidence.MAYBE;
        int options = Anomaly.AnomalyOption.DELAY_128s | Anomaly.AnomalyOption.REQUIRES_HEAVY_COMBAT;
        Anomaly anomaly = Anomaly.anomalyOf("41", confidence, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(player, anomaly);
        if (sprint) {
          plugin.eventService().attackCancelService().requestDamageCancel(user, AttackCancelType.HEAVY);
        } else {
          punishmentData.timeLastSneakToggleCancel = AccessHelper.now();
          Synchronizer.synchronize(() -> ReflectiveDataWatcherAccess.setDataWatcherFlag(player, DATA_WATCHER_SNEAK_ID, false));
        }
      }
    }
  }

  public static final class PacketSprintToggleHeuristicMeta extends UserCustomCheckMeta {
    public int sprintTogglesInTick;
    public int sneakTogglesInTick;

    public void reset() {
      sprintTogglesInTick = 0;
      sneakTogglesInTick = 0;
    }
  }
}