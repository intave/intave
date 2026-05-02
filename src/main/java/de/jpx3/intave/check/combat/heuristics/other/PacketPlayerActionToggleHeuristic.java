package de.jpx3.intave.check.combat.heuristics.other;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
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

import static de.jpx3.intave.entity.datawatcher.DataWatcherAccess.WATCHER_SNEAK_ID;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class PacketPlayerActionToggleHeuristic extends ClassicHeuristic<PacketPlayerActionToggleHeuristic.PacketSprintToggleHeuristicMeta> {
  public PacketPlayerActionToggleHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.SPRINT_TOGGLES, PacketSprintToggleHeuristicMeta.class);
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
    boolean sneak = action.isSneakRelated();
    if (!sprint && !sneak) {
      return;
    }

    if (abilityData.ignoringMovementPackets()) {
      heuristicMeta.reset();
      return;
    }

    if (movementData.lastTeleport < 10) {
      return;
    }

    boolean flag = sprint
      ? heuristicMeta.sprintTogglesInTick++ >= 1
      : heuristicMeta.sneakTogglesInTick++ >= 1;

    if (flag) {
      boolean flyingPacketStream = clientData.flyingPacketsAreSent();
      boolean checkable = flyingPacketStream || !movementData.receivedFlyingPacketIn(20);
      if (checkable) {
        String description = sprint
          ? "sent too many sprint toggles per tick (" + heuristicMeta.sprintTogglesInTick + ")"
          : "sent too many sneak toggles per tick (" + heuristicMeta.sneakTogglesInTick + ")";
        if (!flyingPacketStream) {
          description += " (last flying: " + movementData.pastFlyingPacketAccurate() + ")";
        }
        this.flag(player, description);

        boolean cancel = (flyingPacketStream || Hypot.fast(movementData.motionX(), movementData.motionZ()) > 0.2) && heuristicMeta.threshold++ > 3;
        if (cancel) {
          if (sprint) {
            //dmc12
            user.nerf(AttackNerfStrategy.CANCEL, "sprint:toggles");
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