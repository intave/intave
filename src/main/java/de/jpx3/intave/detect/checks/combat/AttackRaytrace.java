package de.jpx3.intave.detect.checks.combat;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.CheckStatistics;
import de.jpx3.intave.detect.CheckViolationLevelDecrementer;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.punishment.AttackNerfStrategy;
import de.jpx3.intave.event.service.entity.WrappedEntity;
import de.jpx3.intave.event.service.violation.Violation;
import de.jpx3.intave.event.service.violation.ViolationContext;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static de.jpx3.intave.event.service.entity.ClientSideEntityService.entityByIdentifier;
import static de.jpx3.intave.event.service.violation.Violation.ViolationFlags.DONT_PROCESS_VIOSTAT;
import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE;
import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_COMBAT_UPDATE;
import static de.jpx3.intave.world.raytrace.Raytracer.distanceOf;

public class AttackRaytrace extends IntaveMetaCheck<AttackRaytrace.AttackRaytraceMeta> {
  private final IntavePlugin plugin;
  private final CheckViolationLevelDecrementer hitboxDecrementer, reachDecrementer;

  private final double VL_DECREMENT_PER_ATTACK = 0.125;

  public AttackRaytrace(IntavePlugin plugin) {
    super("AttackRaytrace", "attackraytrace", AttackRaytraceMeta.class);
    this.plugin = plugin;
    this.hitboxDecrementer = new CheckViolationLevelDecrementer(this, "applicable-thresholds.hitbox", VL_DECREMENT_PER_ATTACK * 0.5);
    this.reachDecrementer = new CheckViolationLevelDecrementer(this, "applicable-thresholds.reach", VL_DECREMENT_PER_ATTACK * 2);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY")
    }
  )
  public void receiveUseEntityPacket(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    Player player = event.getPlayer();
    AttackRaytraceMeta attackRaytraceMeta = metaOf(player);
    EnumWrappers.EntityUseAction useAction = packet.getEntityUseActions().readSafely(0);
    if (useAction == EnumWrappers.EntityUseAction.ATTACK) {
      PacketContainer packetClone = packet.deepClone();
      int entityId = packet.getIntegers().read(0);
      Attack attack = new Attack(packetClone, entityId);
      if (attackRaytraceMeta.pendingAttacks.size() < 4) {
        attackRaytraceMeta.pendingAttacks.add(attack);
      }
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING")
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    AttackRaytraceMeta attackRaytraceMeta = metaOf(user);
    PacketContainer packet = event.getPacket();
    User.UserMeta meta = user.meta();
    UserMetaClientData clientData = meta.clientData();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();
    if (movementData.lastTeleport == 0) {
      attackRaytraceMeta.pendingAttacks.clear();
      return;
    }
    List<Attack> remainingAttacks = attackRaytraceMeta.pendingAttacks;
    if (!remainingAttacks.isEmpty()) {
      for (Attack remainingAttack : remainingAttacks) {
        statisticApply(user, CheckStatistics::increaseTotal);
        WrappedEntity entity = entityByIdentifier(user, remainingAttack.entityId());
        boolean invalid = false;
        if (entity != null && entity.living() && !player.isDead()) {
          if (clientData.protocolVersion() >= PROTOCOL_VERSION_COMBAT_UPDATE) {
            // >= 1.9.x
            if (entity.clientSynchronized
              && !movementData.recentlyEncounteredFlyingPacket(2)
              && attackRaytraceMeta.lastFlyPacketCounterReach > 1
            ) {
              // 1.9+ beim bewegen
              invalid = processReachCheck(player, entity, 0.1f);
            } else {
              // 1.9+ beim still stehen oder wenn das entity nicht synchronisiert ist
              invalid = processIterativeReachCheck(player, entity);
            }
          }
          if (clientData.protocolVersion() <= PROTOCOL_VERSION_BOUNTIFUL_UPDATE) {
            // <= 1.8.9
            if (!entity.clientSynchronized) {
              // 1.8.x wenn das entity nicht synchronisiert ist
              invalid = processIterativeReachCheck(player, entity);
            } else if (attackRaytraceMeta.lastFlyPacketCounterReach > 1) {
              // 1.8.x beim bewegen
              invalid = processReachCheck(player, entity, 0.1f);
            } else {
              // 1.8.x beim still stehen
              invalid = processReachCheck(player, entity, 0.13f);
            }
          }
        }
        if (invalid) {
          statisticApply(user, CheckStatistics::increaseFails);
        } else {
          statisticApply(user, CheckStatistics::increasePasses);
        }
        if (!invalid && !violationLevelData.isInActiveTeleportBundle) {
          receiveExcludedPacket(player, remainingAttack.packet);
        }
      }
      remainingAttacks.clear();
    }
    boolean hasMovement = packet.getBooleans().read(1);
    if (!hasMovement) {
      attackRaytraceMeta.lastFlyPacketCounterReach = 0;
    } else {
      attackRaytraceMeta.lastFlyPacketCounterReach++;
    }
  }

  private void receiveExcludedPacket(Player player, PacketContainer packet) {
    try {
      userOf(player).ignoreNextPacket();
      ProtocolLibrary.getProtocolManager().recieveClientPacket(player, packet);
    } catch (InvocationTargetException | IllegalAccessException exception) {
      exception.printStackTrace();
    }
  }

  /**
   * @param expandHitbox should be "0.1f" for a default hitbox
   */
  private boolean processReachCheck(Player player, WrappedEntity entity, double expandHitbox) {
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    AttackRaytraceMeta attackRaytraceMeta = metaOf(user);
    UserMetaAttackData attackData = meta.attackData();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    UserMetaPunishmentData punishmentData = meta.punishmentData();

    double blockReachDistance = reachDistance(player.getGameMode() == GameMode.CREATIVE);
    float lastRotationYaw = movementData.lastRotationYaw % 360;
    float rotationYaw = movementData.rotationYaw % 360;
    boolean alternativePositionY = clientData.protocolVersion() == UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE;
    boolean hasAlwaysMouseDelayFix = clientData.protocolVersion() >= 314;

    // mouse delay fix
    Raytracer.EntityInteractionRaytrace distanceOfResult = distanceOf(
      player,
      entity, alternativePositionY,
      movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
      rotationYaw, movementData.rotationPitch,
      expandHitbox
    );
    if (!hasAlwaysMouseDelayFix && distanceOfResult.reach > blockReachDistance) {
      // normal
      distanceOfResult = distanceOf(
        player,
        entity, true,
        movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
        lastRotationYaw, movementData.rotationPitch,
        expandHitbox
      );
    }

    attackData.setLastReach(distanceOfResult.reach);
    String message, details, thresholdKey, special;
    AttackRaytraceResult attackRaytraceResult = AttackRaytrace.AttackRaytraceResult.of(distanceOfResult.reach, blockReachDistance);
    final int vl = applicableViolationPoints(attackRaytraceResult, distanceOfResult, user, expandHitbox);
    String entityName = entity.entityName();

    switch (attackRaytraceResult) {
      case MISS: {
        message = "attacked " + resolveIndefArticle(entityName) + " " + entityName.toLowerCase() + " out of sight";
        details = "";
        thresholdKey = "applicable-thresholds.hitbox";
        special = ChatColor.RED + "[R] " + player.getName() + " missed hit on " + entityName.toLowerCase();
        break;
      }
      case REACH: {
        String displayReach = MathHelper.formatDouble(distanceOfResult.reach, 4);
        message = "attacked " + resolveIndefArticle(entityName) + " " + entityName.toLowerCase() + " from too far away";
        details = displayReach + " blocks";
        thresholdKey = "applicable-thresholds.reach";
        special = ChatColor.RED + "[R] " + player.getName() + " attacked " + entityName.toLowerCase() + " from " + displayReach;
        break;
      }
      default: {
        hitboxDecrementer.decrement(user, VL_DECREMENT_PER_ATTACK);
        reachDecrementer.decrement(user, VL_DECREMENT_PER_ATTACK);
        if (punishmentData.nerferOfType(AttackNerfStrategy.CANCEL_FIRST_HIT).active()) {
          double moved = Math.hypot(movementData.motionX(), movementData.motionZ());
          return moved > 0.1 && distanceOfResult.reach > 2.8;
        }
        return false;
      }
    }

    // still required?!
    Synchronizer.synchronize(new Runnable() {
      @Native
      @Override
      public void run() {
        for (Player authenticatedPlayer : Bukkit.getOnlinePlayers()) {
          if (plugin.sibylIntegrationService().isAuthenticated(authenticatedPlayer)) {
            authenticatedPlayer.sendMessage(special);
          }
        }
      }
    });

    attackRaytraceMeta.lastHitVec = distanceOfResult.hitVec;
    if (movementData.inVehicle()) {
      message += " (vehicle)";
    }

    Violation violation = Violation.builderFor(AttackRaytrace.class)
      .withPlayer(player).withMessage(message).withDetails(details)
      .withCustomThreshold(thresholdKey).withVL(vl)
      .build();
    ViolationContext violationContext = plugin.violationProcessor().processViolation(violation);
    if (violationContext.violationLevelAfter() > 50) {
      user.applyAttackNerfer(AttackNerfStrategy.DMG_MEDIUM);
    }
    return true;
  }

  private int applicableViolationPoints(
    AttackRaytraceResult attackRaytraceResult,
    Raytracer.EntityInteractionRaytrace distanceOfResult,
    User user, double expandHitbox
  ) {
    AttackRaytraceMeta attackRaytraceMeta = metaOf(user);
    UserMetaMovementData movementData = user.meta().movementData();
    int vl = 0;
    switch (attackRaytraceResult) {
      case MISS: {
        vl = 4;
        break;
      }
      case REACH: {
        vl = 20;
        break;
      }
      default: {
        break;
      }
    }
    if (expandHitbox > 0.1f) {
      vl /= 2;
    }
    if (movementData.inVehicle()) {
      vl = 0;
    } else if (distanceOfResult.hitVec != null && attackRaytraceMeta.lastHitVec != null && distanceOfResult.hitVec.distanceTo(attackRaytraceMeta.lastHitVec) == 0) {
      vl = 0;
    }
    return vl;
  }

  private Player playerByWrappedEntity(WrappedEntity wrappedEntity) {
    int entityId = wrappedEntity.entityId();
    for (Player player : Bukkit.getServer().getOnlinePlayers()) {
      if (player.getEntityId() == entityId) {
        return player;
      }
    }
    return null;
  }

  private boolean processIterativeReachCheck(Player player, WrappedEntity attackedEntity) {
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();

    double blockReachDistance = reachDistance(player.getGameMode() == GameMode.CREATIVE);
    float lastRotationYaw = movementData.lastRotationYaw % 360;
    float rotationYaw = movementData.rotationYaw % 360;
    boolean hasAlwaysMouseDelayFix = clientData.protocolVersion() >= 314;
    double minReach = 10;
    WrappedEntity clonedEntity = attackedEntity.clone();
    for (WrappedEntity.EntityPositionContext possiblePosition : clonedEntity.positionHistory) {
      // TODO: 01/07/21 add trust-factor based length tolerance
      clonedEntity.setPositionAndRotationEntityLiving(possiblePosition.posX, possiblePosition.posY, possiblePosition.posZ, 3);
      double minReachInItr = 10;
      for (int loopRotationIncrement = 0; loopRotationIncrement < 4; loopRotationIncrement++) {
        // mouse delay fix
        Raytracer.EntityInteractionRaytrace result = distanceOf(
          player,
          clonedEntity,
          false,
          movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
          rotationYaw, movementData.rotationPitch,
          0.13f
        );
        if (!hasAlwaysMouseDelayFix && result.reach > blockReachDistance) {
          // normal
          result = distanceOf(
            player,
            clonedEntity,
            false,
            movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
            lastRotationYaw, movementData.rotationPitch,
            0.13f
          );
        }
        minReachInItr = Math.min(minReachInItr, result.reach);
        clonedEntity.onUpdate();
      }
      minReach = Math.min(minReach, minReachInItr);
    }
    // TODO: 01/07/21 clear after last possible position
    if (minReach > blockReachDistance) {
      String entityName = attackedEntity.entityName();
      String targetDescriptor = resolveIndefArticle(entityName) + " " + entityName.toLowerCase();
      String thresholdKey = "";
      String message, details;
      if (minReach == 10) {
        message = "attacked " + targetDescriptor + " out of sight";
        details = "estimated";
        thresholdKey = "applicable-thresholds.hitbox";
      } else {
        String minReachDisplay = MathHelper.formatDouble(minReach, 4) + " blocks";
        message = "attacked " + targetDescriptor + " from too far away";
        details = minReachDisplay + " at best, estimated";
        thresholdKey = "applicable-thresholds.reach";
      }
      if (movementData.inVehicle()) {
        message += " (vehicle)";
      }

      Violation violation = Violation.builderFor(AttackRaytrace.class)
        .withPlayer(player).withMessage(message).withDetails(details)
        .withCustomThreshold(thresholdKey).withVL(0)
        .appendFlags(DONT_PROCESS_VIOSTAT)
        .build();
      plugin.violationProcessor().processViolation(violation);
      return true;
    }
    hitboxDecrementer.decrement(user, VL_DECREMENT_PER_ATTACK);
    reachDecrementer.decrement(user, VL_DECREMENT_PER_ATTACK);
    return false;
  }

  private final static char[] vocals = "aeiou".toCharArray();

  private String resolveIndefArticle(String exceptionName) {
    char c = exceptionName.trim().toLowerCase(Locale.ROOT).toCharArray()[0];
    boolean isVocal = false;
    for (char vocal : vocals) {
      if (vocal == c) {
        isVocal = true;
        break;
      }
    }
    return isVocal ? "an" : "a";
  }

  public static float reachDistance(boolean creative) {
    return (creative ? 5.0F : 3.0F) + 0.005f;
  }

  public static class AttackRaytraceMeta extends UserCustomCheckMeta {
    public int lastFlyPacketCounterReach = 0;
    public List<Attack> pendingAttacks = new ArrayList<>();
    public int confidence;
    public WrappedVector lastHitVec;
  }

  public static class Attack {
    private final PacketContainer packet;
    private final int entityId;

    public Attack(PacketContainer packet, int entityId) {
      this.packet = packet;
      this.entityId = entityId;
    }

    public PacketContainer packet() {
      return packet;
    }

    public int entityId() {
      return entityId;
    }
  }

  public enum AttackRaytraceResult {
    NORMAL,
    REACH,
    MISS;

    public static AttackRaytraceResult of(double reach, double reachLimit) {
      if (reach == 10.0) {
        return AttackRaytrace.AttackRaytraceResult.MISS;
      } else if (reach > reachLimit) {
        return AttackRaytrace.AttackRaytraceResult.REACH;
      }
      return AttackRaytrace.AttackRaytraceResult.NORMAL;
    }
  }
}