package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.MetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.violation.AttackNerfStrategy;
import de.jpx3.intave.reflect.ReflectiveDataWatcherAccess;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.packet.PlayerAction;
import de.jpx3.intave.tools.packet.PlayerActionResolver;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.*;
import org.bukkit.entity.Player;

import static de.jpx3.intave.event.packet.PacketId.Client.*;
import static de.jpx3.intave.reflect.ReflectiveDataWatcherAccess.WATCHER_SNEAK_ID;

public final class PacketPlayerActionToggleHeuristic extends MetaCheckPart<Heuristics, PacketPlayerActionToggleHeuristic.PacketSprintToggleHeuristicMeta> {
  private final IntavePlugin plugin;
  private boolean enabled;

  public PacketPlayerActionToggleHeuristic(Heuristics parentCheck) {
    super(parentCheck, PacketSprintToggleHeuristicMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    PacketSprintToggleHeuristicMeta heuristicMeta = metaOf(player);
    heuristicMeta.reset();
  }

  @PacketSubscription(
    packetsIn = {
      ENTITY_ACTION
    }
  )
  public void receiveEntityAction(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    UserMeta meta = user.meta();
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
          : "sent too many sneak toggles per tick (" + heuristicMeta.sneakTogglesInTick + ")";
        if (!flyingPacketStream) {
          description += " (last flying: " + movementData.pastFlyingPacketAccurate() + ")";
        }
        if (this.enabled) {
          // could be CERTAIN on 1.8
          Confidence confidence = flyingPacketStream ? Confidence.PROBABLE : Confidence.MAYBE;
          int options = Anomaly.AnomalyOption.DELAY_128s | Anomaly.AnomalyOption.REQUIRES_HEAVY_COMBAT;
          Anomaly anomaly = Anomaly.anomalyOf("41", confidence, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
        }

        boolean cancel = flyingPacketStream || heuristicMeta.threshold++ > 2 && Math.hypot(movementData.motionX(), movementData.motionZ()) > 0.2;
        if (cancel) {
          if (sprint) {
            //dmc12
            user.applyAttackNerfer(AttackNerfStrategy.CANCEL, "12");
          } else {
            punishmentData.timeLastSneakToggleCancel = AccessHelper.now();
            Synchronizer.synchronize(() -> ReflectiveDataWatcherAccess.setDataWatcherFlag(player, WATCHER_SNEAK_ID, false));
          }
        }
      }
    } else if (heuristicMeta.threshold > 0) {
      heuristicMeta.threshold -= 0.025;
    }
  }

  @Override
  public boolean enabled() {
    this.enabled = super.enabled();
    return true;
  }

  public static final class PacketSprintToggleHeuristicMeta extends UserCustomCheckMeta {
    public int sprintTogglesInTick;
    public int sneakTogglesInTick;
    public double threshold;

    public void reset() {
      sprintTogglesInTick = 0;
      sneakTogglesInTick = 0;
    }
  }
}