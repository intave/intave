package de.jpx3.intave.check.combat.heuristics.detect.clickpatterns;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.util.concurrent.AtomicDouble;
import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.Blueprint;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.share.MovingObjectPosition;
import de.jpx3.intave.share.NativeVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

@Reserved
public abstract class SwingBlueprint<M extends SwingBlueprintMeta>
  extends Blueprint<Heuristics, SwingBlueprintMeta, M> {
  private final int sampleSize;
  // Could use bit-shift operations for these options in constructor? they're always true & false for now
  private final boolean ignoreDoubleClicks;
  // This could be done inside the Anomaly system with REQUIRES_HEAVY_COMBAT, but doing it here allow us to just
  // pause sample instead of voiding them and also enter specific conditions
  private final boolean requireCombat;

  public SwingBlueprint(Heuristics parentCheck, Class<M> metaClass, int sampleSize, boolean ignoreDoubleClicks, boolean requireCombat) {
    super(parentCheck, metaClass);
    this.sampleSize = sampleSize;
    this.ignoreDoubleClicks = ignoreDoubleClicks;
    this.requireCombat = requireCombat;
  }

  // Could use bit-shift operations for these options in constructor?
  public SwingBlueprint(Heuristics parentCheck, Class<M> metaClass, int sampleSize) {
    super(parentCheck, metaClass);
    this.sampleSize = sampleSize;
    this.ignoreDoubleClicks = true;
    this.requireCombat = false;
  }

  public abstract void check(User user, List<Integer> delays);

  private boolean lookingAtBlocks(User user) {
    Player player = user.player();
    World world = user.player().getWorld();
    MovementMetadata movementData = user.meta().movement();
    Location playerLocation = new Location(world,
      movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
      movementData.rotationYaw, movementData.rotationPitch);

    MovingObjectPosition raytraceResult;
    try {
      raytraceResult = Raytracing.blockRayTrace(player, playerLocation);
    } catch (Exception exception) {
      exception.printStackTrace();
      return true;
    }
    return raytraceResult != null && raytraceResult.hitVec != NativeVector.ZERO;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void clientSwing(PacketEvent event) {
    User user = userOf(event.getPlayer());
    SwingBlueprintMeta meta = metaOf(user);
    // SwingBlueprint detections only work on 1.8- !
    if (!user.meta().protocol().flyingPacketsAreSent()) {
      return;
    }
    // Completely ignore this swing, like it never existed!
    if (user.meta().attack().inBreakProcess || meta.placedBlock) {
      return;
    }
    boolean requireCombatCheck = !requireCombat || meta.lastAttack <= 10;
    boolean ignoreDoubleClickCheck = !ignoreDoubleClicks || meta.delay > 0;
    if (meta.delay <= 15 && meta.lastDelay <= 15 && requireCombatCheck && ignoreDoubleClickCheck) {
      SwingBlueprintMeta.ClickData clickData = new SwingBlueprintMeta.ClickData(meta.delay, meta.lastDelay);
      meta.pendingClicks.add(clickData);
    }
    meta.lastDelay = meta.delay;
    meta.delay = 0;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      BLOCK_PLACE
    }
  )
  // BLOCK_PLACE is replaced by USE_ITEM in 1.9+ but it doesn't matter to us since those detections are for 1.8-
  public void clientBlockPlace(PacketEvent event) {
    User user = userOf(event.getPlayer());
    SwingBlueprintMeta meta = metaOf(user);
    meta.placedBlock = true;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void clientUseEntity(PacketEvent event) {
    User user = userOf(event.getPlayer());
    SwingBlueprintMeta meta = metaOf(user);
    PacketContainer packet = event.getPacket();
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
    if (action == null) {
      action = packet.getEnumEntityUseActions().read(0).getAction();
    }
    if (action == EnumWrappers.EntityUseAction.ATTACK) {
      meta.lastAttack = 0;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void clientTickUpdate(PacketEvent event) {
    User user = userOf(event.getPlayer());
    SwingBlueprintMeta meta = metaOf(user);
    for (SwingBlueprintMeta.ClickData pendingClick : meta.pendingClicks) {
      if (meta.lastAttack > 0 && lookingAtBlocks(user)) {
        continue;
      }
      if (pendingClick.delay == 0) {
        meta.doubleClicks++;
      }
      meta.delaysDelta.add(Math.abs(pendingClick.delay - pendingClick.lastDelay));
      meta.delays.add(pendingClick.delay);
      if (meta.delays.size() == sampleSize) {
        check(user, meta.delays);
        meta.delays.clear();
        meta.delaysDelta.clear();
        meta.doubleClicks = 0;
      }
    }
    meta.pendingClicks.clear();
    meta.delay++;
    meta.lastAttack++;
    meta.placedBlock = false;
  }

  public double clickPerSecond(List<Integer> delays) {
    return 20 / average(delays);
  }

  public double standardDeviation(List<Integer> values) {
    double average = average(values);
    AtomicDouble variance = new AtomicDouble(0D);
    values.forEach(delay -> variance.getAndAdd(Math.pow(delay.doubleValue() - average, 2D)));
    return Math.sqrt(variance.get() / values.size());
  }

  public double average(List<Integer> values) {
    return values.stream()
      .mapToDouble(Number::doubleValue)
      .average()
      .orElse(0D);
  }
}
