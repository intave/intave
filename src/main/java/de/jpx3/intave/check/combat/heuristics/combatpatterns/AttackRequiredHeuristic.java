package de.jpx3.intave.check.combat.heuristics.combatpatterns;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.raytrace.Raytrace;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_8;

public final class AttackRequiredHeuristic extends ClassicHeuristic<AttackRequiredHeuristic.AttackRequiredMeta> {
  public AttackRequiredHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ATTACK_REQUIRED, AttackRequiredMeta.class);
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
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveSlotSwitch(PacketEvent event) {
    Player player = event.getPlayer();
    AttackRequiredMeta meta = metaOf(player);
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
    Entity entity = attackData.lastAttackedEntity();
    if (entity == null || !entity.clientSynchronized || movementData.lastTeleport < 5) {
      return;
    }
    // todo: test if it works on versions > 1.8
    if (clientData.protocolVersion() != VER_1_8 || clientData.outdatedClient()) {
      return;
    }

    boolean dead = entity.fakeDead || entity.dead;
    if (dead) {
      return;
    }
    AttackRequiredMeta meta = metaOf(player);

    // FishingRod overrides onItemRightClick and sends an arm-animation packet
    boolean recentlyUsedRot = meta.ticksSinceFishingRodItemSwitch < 5;

    if (!recentlyUsedRot && meta.didSwing && !meta.didAttack) {
      // Raytrace if cursor is upon entity
      boolean cursorUponEntity = cursorUponEntity(user, entity);
      if (cursorUponEntity) {
        long timeToLastFlag = System.currentTimeMillis() - meta.lastFlag;
        if (timeToLastFlag < 20_000 && timeToLastFlag > 1500) {
          int vl = (meta.vl += 200) / 200;
          if (vl >= 2) {
            flag(player, "missed attack packet vl:" + vl);
          }
        }
        meta.lastFlag = System.currentTimeMillis();
      }
    }

    if (meta.didSwing && meta.didAttack && meta.vl > 0) {
      meta.vl--;
    }

    meta.ticksSinceFishingRodItemSwitch++;

    meta.expectedAttack = false;
    meta.didAttack = false;
    meta.didSwing = false;
  }

  private boolean cursorUponEntity(
    User user,
    Entity entity
  ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    float expandHitbox = 0.05f;
    double blockReachDistance = reachDistance(player.getGameMode() == GameMode.CREATIVE);
    float lastRotationYaw = movementData.lastRotationYaw % 360;
    float rotationYaw = movementData.rotationYaw % 360;
    boolean alternativePositionY = clientData.protocolVersion() == VER_1_8;
    boolean hasAlwaysMouseDelayFix = clientData.protocolVersion() >= 314;
    // mouse delay fix
    Raytrace distanceOfResult = Raytracing.blockConstraintEntityRaytrace(
      player,
      entity, alternativePositionY,
      movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
      rotationYaw, movementData.rotationPitch,
      expandHitbox
    );
    if (distanceOfResult.reach() > blockReachDistance) {
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
    return distanceOfResult.reach() <= blockReachDistance;
  }

  private float reachDistance(boolean creative) {
    return (creative ? 5.0F : 3.0F) - 0.005f;
  }

  public static final class AttackRequiredMeta extends CheckCustomMetadata {
    public boolean expectedAttack;
    public boolean didAttack, didSwing;
    public long lastFlag;
    public int vl;
    public int ticksSinceFishingRodItemSwitch;
  }
}