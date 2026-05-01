package de.jpx3.intave.check.combat.heuristics.other;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class NoSwingHeuristic extends ClassicHeuristic<NoSwingHeuristic.NoSwingMeta> {

  public NoSwingHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.NO_SWING, NoSwingMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void entityHit(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    NoSwingMeta meta = metaOf(user);

    PacketContainer packet = event.getPacket();
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
    if (action == null) {
      action = packet.getEnumEntityUseActions().read(0).getAction();
    }
    if (action == EnumWrappers.EntityUseAction.ATTACK) {
//      if (meta.swingsThisTick == 0 && meta.attacksThisTick == 0
//        && user.meta().clientData().protocolVersion() == 47
//      ) {
//        event.setCancelled(true);
//      }
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
    MovementMetadata movementData = user.meta().movement();
    NoSwingMeta meta = metaOf(user);

    if (movementData.lastTeleport == 0) {
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
