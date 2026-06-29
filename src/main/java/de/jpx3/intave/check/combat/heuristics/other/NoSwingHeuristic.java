package de.jpx3.intave.check.combat.heuristics.other;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class NoSwingHeuristic extends ClassicHeuristic<NoSwingHeuristic.NoSwingMeta> {

  public NoSwingHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.NO_SWING, NoSwingMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void entityHit(
    Player player, EntityUseReader reader
  ) {
    User user = userOf(player);
    NoSwingMeta meta = metaOf(user);
    if (reader.isAttackPacket()) {
      meta.attacksThisTick++;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void swing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    NoSwingMeta meta = metaOf(user);

    meta.swingsThisTick++;
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    SimulationEnvironment movementData = user.meta().movement();
    NoSwingMeta meta = metaOf(user);

    if (movementData.ticksPast(TELEPORT) == 0) {
      return;
    }

    // todo: fix?
    if (user.meta().protocol().outdatedClient()) {
      return;
    }

    if (meta.attacksThisTick > 0) {
      if (meta.swingsThisTick == 0) {
        String details = "missing swing packet on attack";
        String checkName = "swing:miss";
        flag(player, details);
        user.nerf(AttackNerfStrategy.CANCEL, checkName);
      }
    }

    prepareNextTick(meta);
  }

  private void prepareNextTick(NoSwingMeta meta) {
    meta.swingsThisTick = 0;
    meta.attacksThisTick = 0;
  }

  public static class NoSwingMeta extends CheckCustomMetadata {
    public int swingsThisTick;
    public int attacksThisTick;
  }
}
