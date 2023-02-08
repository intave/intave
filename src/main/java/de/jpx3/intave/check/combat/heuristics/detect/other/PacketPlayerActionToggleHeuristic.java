package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.entity.datawatcher.DataWatcherAccess;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.converter.PlayerAction;
import de.jpx3.intave.packet.converter.PlayerActionResolver;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.*;
import static de.jpx3.intave.entity.datawatcher.DataWatcherAccess.WATCHER_SNEAK_ID;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

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
      ENTITY_ACTION_IN
    }
  )
  public void receiveEntityAction(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    AbilityMetadata abilityData = meta.abilities();
    ProtocolMetadata clientData = meta.protocol();
    PunishmentMetadata punishmentData = user.meta().punishment();
    PacketSprintToggleHeuristicMeta heuristicMeta = metaOf(user);

    PacketContainer packet = event.getPacket();
    PlayerAction action = PlayerActionResolver.resolveActionFromPacket(packet);

    boolean sprint = action == PlayerAction.START_SPRINTING || action == PlayerAction.STOP_SPRINTING;
    boolean sneak = action.isSneaking();
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
      boolean flyingPacketStream = clientData.flyingPacketsAreSent();
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
          int options = DELAY_128s | REQUIRES_HEAVY_COMBAT | LIMIT_4;
          Anomaly anomaly = Anomaly.anomalyOf("41", confidence, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
        }

        boolean cancel = flyingPacketStream || Hypot.fast(movementData.motionX(), movementData.motionZ()) > 0.1 && heuristicMeta.threshold++ > 3;
        if (cancel) {
          if (sprint) {
            //dmc12
            user.nerf(AttackNerfStrategy.CANCEL, "12");
          } else {
            punishmentData.timeLastSneakToggleCancel = System.currentTimeMillis();
            Synchronizer.synchronize(() -> DataWatcherAccess.setDataWatcherFlag(player, WATCHER_SNEAK_ID, false));
          }
        }
      }
    } else if (heuristicMeta.threshold > 0) {
      heuristicMeta.threshold -= 0.01;
    }
  }

  @Override
  public boolean enabled() {
    this.enabled = super.enabled();
    return true;
  }

  public static final class PacketSprintToggleHeuristicMeta extends CheckCustomMetadata {
    public int sprintTogglesInTick;
    public int sneakTogglesInTick;
    public double threshold;

    public void reset() {
      sprintTogglesInTick = 0;
      sneakTogglesInTick = 0;
    }
  }
}