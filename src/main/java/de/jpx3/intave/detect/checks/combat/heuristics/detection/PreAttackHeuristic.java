package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.MetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.WrappedEntity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.detect.checks.combat.heuristics.Anomaly.AnomalyOption.LIMIT_4;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_8;

public final class PreAttackHeuristic extends MetaCheckPart<Heuristics, PreAttackHeuristic.PreAttackMeta> {
  private final IntavePlugin plugin;

  public PreAttackHeuristic(Heuristics parentCheck) {
    super(parentCheck, PreAttackMeta.class);
    plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void receiveSwing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    metaOf(user).didSwing = true;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void receiveAttack(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
    if (action == null) {
      action = packet.getEnumEntityUseActions().read(0).getAction();
    }
    if (action == EnumWrappers.EntityUseAction.ATTACK) {
      metaOf(player).didAttack = true;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      HELD_ITEM_SLOT
    }
  )
  public void receiveSlotSwitch(PacketEvent event) {
    Player player = event.getPlayer();
    PreAttackMeta meta = metaOf(player);
    PacketContainer packet = event.getPacket();
    Integer slot = packet.getIntegers().read(0);

    ItemStack item = player.getInventory().getItem(slot);
    if (item == null) {
      return;
    }
    meta.ticksSinceFishingRodItemSwitch = 0;
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    AttackMetadata attackData = user.meta().attack();
    MovementMetadata movementData = user.meta().movement();
    WrappedEntity entity = attackData.lastAttackedEntity();

    if (entity == null || !entity.clientSynchronized || movementData.lastTeleport < 5) {
      return;
    }

    if (clientData.protocolVersion() != VER_1_8 || clientData.clientVersionOlderThanServerVersion()) {
      return;
    }

    boolean dead = entity.fakeDead || entity.dead;
    if (dead) {
      return;
    }

    PreAttackMeta meta = metaOf(player);

    try {
      if (!entity.moving(0.1) || attackData.lastReach() < 1.0) {
        return;
      }

      // FishingRod overrides onItemRightClick and sends an arm-animation packet
      boolean recentlyUsedRot = meta.ticksSinceFishingRodItemSwitch < 5;

      if (!recentlyUsedRot && meta.didSwing && !meta.didAttack) {
        // Raytrace if cursor is upon entity
        boolean cursorUponEntity = cursorUponEntity(player, user, entity);
        if (cursorUponEntity) {
          meta.preAttacks++;
        }
      }

      if (meta.didAttack) {
        meta.attacks++;
      }

      if (meta.attacks >= 100) {
//        player.sendMessage((((double)meta.preAttacks / ((double)meta.preAttacks + (double) meta.attacks) * 100) + "% unsuccessful"));
        if (meta.preAttacks < 4) {
          String description = "attacks seem automated (" + meta.preAttacks + "f)";
          Anomaly anomaly = Anomaly.anomalyOf("231", Confidence.MAYBE, Anomaly.Type.KILLAURA, description, LIMIT_4);
          parentCheck().saveAnomaly(player, anomaly);
        }

        meta.attacks = 0;
        meta.preAttacks = 0;
      }
    } finally {
      meta.ticksSinceFishingRodItemSwitch++;
      meta.didAttack = false;
      meta.didSwing = false;
    }
  }

  private boolean cursorUponEntity(
    Player player,
    User user,
    WrappedEntity entity
  ) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    float expandHitbox = 0.25f /* EXPAND */;
    double blockReachDistance = reachDistance(player.getGameMode() == GameMode.CREATIVE) + 1f /* RANGE */;
    float lastRotationYaw = movementData.lastRotationYaw % 360;
    float rotationYaw = movementData.rotationYaw % 360;
    boolean alternativePositionY = clientData.protocolVersion() == VER_1_8;
    boolean hasAlwaysMouseDelayFix = clientData.protocolVersion() >= 314;
    // mouse delay fix
    Raytracing.EntityInteractionRaytrace distanceOfResult = Raytracing.blockConstraintEntityRaytrace(
      player,
      entity, alternativePositionY,
      movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
      rotationYaw, movementData.rotationPitch,
      expandHitbox
    );
    if (distanceOfResult.reach > blockReachDistance) {
      return false;
    }
    if (!hasAlwaysMouseDelayFix) {
      // normal
      distanceOfResult = Raytracing.blockConstraintEntityRaytrace(
        player,
        entity, true,
        movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
        lastRotationYaw, movementData.rotationPitch,
        expandHitbox
      );
    }
    return distanceOfResult.reach <= blockReachDistance;
  }

  private float reachDistance(boolean creative) {
    return (creative ? 5.0F : 3.0F) - 0.005f;
  }

  public final static class PreAttackMeta extends CheckCustomMetadata {
    public boolean didAttack, didSwing;
    public int ticksSinceFishingRodItemSwitch;

    public int preAttacks, attacks;
  }
}
