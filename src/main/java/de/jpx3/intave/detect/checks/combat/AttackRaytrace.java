package de.jpx3.intave.detect.checks.combat;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.CheckStatistics;
import de.jpx3.intave.detect.CheckViolationLevelDecrementer;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.event.entity.WrappedEntity;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.violation.AttackNerfStrategy;
import de.jpx3.intave.event.violation.Violation;
import de.jpx3.intave.event.violation.ViolationContext;
import de.jpx3.intave.logging.IntaveLogger;
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

import static de.jpx3.intave.event.entity.ClientSideEntityService.entityByIdentifier;
import static de.jpx3.intave.event.packet.PacketId.Client.*;
import static de.jpx3.intave.event.violation.Violation.ViolationFlags.DONT_PROCESS_VIOSTAT;
import static de.jpx3.intave.user.UserMetaClientData.VER_1_9;

public final class AttackRaytrace extends IntaveMetaCheck<AttackRaytrace.AttackRaytraceMeta> {
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
    packetsIn = {
      USE_ENTITY
    }
  )
  public void receiveUseEntityPacket(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    Player player = event.getPlayer();
    User user = userOf(player);
    AttackRaytraceMeta attackRaytraceMeta = metaOf(player);
    UserMetaViolationLevelData violationLevelData = user.meta().violationLevelData();
    EnumWrappers.EntityUseAction useAction = packet.getEntityUseActions().readSafely(0);

    if (useAction == EnumWrappers.EntityUseAction.ATTACK) {
      PacketContainer packetClone = packet.deepClone();
      int entityId = packet.getIntegers().read(0);

      boolean shouldResend;
      UserMetaMovementData movementData = user.meta().movementData();
      WrappedEntity entity = entityByIdentifier(user, entityId);
      UserMetaClientData clientData = user.meta().clientData();
      UserMetaAbilityData abilityData = user.meta().abilityData();
      float unsynchroniszedHealth = abilityData.unsynchroniszedHealth;

      if (entity == null || unsynchroniszedHealth <= 0) {
        shouldResend = true;
      } else {
        if (movementData.lastTeleport == 0 || violationLevelData.isInActiveTeleportBundle) {
          shouldResend = true;
        } else {
          if ((entity.clientSynchronized && !movementData.recentlyEncounteredFlyingPacket(2) && attackRaytraceMeta.lastFlyPacketCounterReach > 1)
            || clientData.protocolVersion() == UserMetaClientData.VER_1_8) {
            shouldResend = validReachWalking(user, entity);
          } else {
            shouldResend = validReachStanding(user, entity);
          }
        }
      }

      if (shouldResend) {
        event.setCancelled(true);
      }

      Attack attack = new Attack(packetClone, entityId, shouldResend);
      if (attackRaytraceMeta.pendingAttacks.size() < 4) {
        attackRaytraceMeta.pendingAttacks.add(attack);
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
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
    for (Attack remainingAttack : remainingAttacks) {
      statisticApply(user, CheckStatistics::increaseTotal);
      WrappedEntity entity = entityByIdentifier(user, remainingAttack.entityId());
      Boolean cancelHit = null;
      UserMetaAbilityData abilityData = user.meta().abilityData();
      float unsynchroniszedHealth = abilityData.unsynchroniszedHealth;

      // stops raytrace if the entity is null or the player is in the death screen
      if (unsynchroniszedHealth > 0) {
        // bypass when the entity is null or on entities which are riding and players which are mounted on entities
        if(entity != null) {
          if (entity.mountedEntity() == null && !player.isInsideVehicle() && entity.isEntityLiving && !abilityData.ignoringMovementPackets()) {
            if (clientData.protocolVersion() >= VER_1_9) {
              // >= 1.9.x
              if (entity.clientSynchronized
                && !movementData.recentlyEncounteredFlyingPacket(2)
                && attackRaytraceMeta.lastFlyPacketCounterReach > 1
              ) {
                // 1.9+ beim bewegen
                cancelHit = processReachCheck(player, entity, 0.1f);
              } else {
                // 1.9+ beim still stehen oder wenn das entity nicht synchronisiert ist
                cancelHit = processIterativeReachCheck(player, entity);
              }
            } else {
              // <= 1.8.9
              if (!entity.clientSynchronized) {
                // 1.8.x wenn das entity nicht synchronisiert ist
                cancelHit = processIterativeReachCheck(player, entity);
              } else if (attackRaytraceMeta.lastFlyPacketCounterReach > 1) {
                // 1.8.x beim bewegen
                cancelHit = processReachCheck(player, entity, 0.1f);
              } else {
                // 1.8.x beim still stehen
                cancelHit = processReachCheck(player, entity, 0.13f);
              }
            }

            if (cancelHit) {
              statisticApply(user, CheckStatistics::increaseFails);
            }
          }
        } else {
          if(IntaveControl.DISABLE_LICENSE_CHECK) {
            IntaveLogger.logger().error(player.getName() + " attacked a null entity");
          }
          Synchronizer.synchronize(new Runnable() {
            @Native
            @Override
            public void run() {
              for (Player authenticatedPlayer : Bukkit.getOnlinePlayers()) {
                if (plugin.sibylIntegrationService().isAuthenticated(authenticatedPlayer)) {
                  String message;
                  message = ChatColor.RED + "[R] " + player.getName() + " attacked a null entity";
                  authenticatedPlayer.sendMessage(message);
                }
              }
            }
          });
        }
      } else {
        cancelHit = true;
      }
      if(cancelHit == null || !cancelHit) {
        if (!violationLevelData.isInActiveTeleportBundle && remainingAttack.shouldResend) {
          receiveExcludedPacket(player, remainingAttack.packet);
        }
        // increaseFails should not be increased here because hits can be canceled when health are under 0
        statisticApply(user, CheckStatistics::increasePasses);
      }
    }
    remainingAttacks.clear();
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

  private boolean validReachStanding(User user, WrappedEntity entity) {
    Player player = user.player();
    double minReach = findLowestPossibleReachIterative(user, entity, false, true);
    double blockReachDistance = Raytracer.reachDistance(player);

    return minReach > blockReachDistance;
  }

  private boolean validReachWalking(User user, WrappedEntity entity) {
    UserMetaMovementData movementData = user.meta().movementData();
    Player player = user.player();
    double blockReachDistance = Raytracer.reachDistance(player);
    float rotationYaw = movementData.rotationYaw % 360;

    // mouse delay fix
    Raytracer.EntityInteractionRaytrace distanceOfResult = Raytracer.distanceOfCombo(
      player,
      entity, true,
      movementData.positionX, movementData.positionY, movementData.positionZ,
      rotationYaw,
      rotationYaw, movementData.rotationPitch,
      0.1f,
      false
    );

    return distanceOfResult.reach > blockReachDistance;
  }

  /**
   * @param expandHitbox should be "0.1f" for a default hitbox
   */
  private boolean processReachCheck(Player player, WrappedEntity entity, double expandHitbox) {
    User user = userOf(player);
    User.UserMeta meta = user.meta();
    AttackRaytraceMeta attackRaytraceMeta = metaOf(user);
    UserMetaAttackData attackData = meta.attackData();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    UserMetaPunishmentData punishmentData = meta.punishmentData();

    double blockReachDistance = Raytracer.reachDistance(meta);
    boolean alternativePositionY = clientData.protocolVersion() == UserMetaClientData.VER_1_8;
    boolean hasAlwaysMouseDelayFix = clientData.protocolVersion() >= 314;
    float rotationYaw = movementData.rotationYaw % 360f;
    float lastRotationYaw = movementData.lastRotationYaw % 360f;

    // mouse delay fix
    Raytracer.EntityInteractionRaytrace distanceOfResult = Raytracer.distanceOfCombo(
      player,
      entity, alternativePositionY,
      movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
      lastRotationYaw,
      rotationYaw, movementData.rotationPitch,
      expandHitbox,
      !hasAlwaysMouseDelayFix
    );

    attackData.setLastReach(distanceOfResult.reach);
    String message, details, thresholdKey, special;
    AttackRaytraceResult attackRaytraceResult = AttackRaytrace.AttackRaytraceResult.of(distanceOfResult.reach, blockReachDistance);
    final int vl = applicableViolationPoints(attackRaytraceResult, distanceOfResult, entity, user, expandHitbox);
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
//    if (movementData.inVehicle()) {
//      message += " (vehicle)";
//    }

//    if (entity.verifiedPosition) {
//      message += " (verified)";
//    }

    Violation violation = Violation.builderFor(AttackRaytrace.class)
      .forPlayer(player).withMessage(message).withDetails(details)
      .withCustomThreshold(thresholdKey).withVL(vl)
      .build();
    ViolationContext violationContext = plugin.violationProcessor().processViolation(violation);
    if (violationContext.violationLevelAfter() > 50) {
      //dmc3
      user.applyAttackNerfer(AttackNerfStrategy.DMG_MEDIUM, "3");
    }
    return true;
  }

  private int applicableViolationPoints(
    AttackRaytraceResult attackRaytraceResult,
    Raytracer.EntityInteractionRaytrace distanceOfResult,
    WrappedEntity entity,
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
    } else if (entity.verifiedPosition) {
      vl *= 1.5;
    }
    if (movementData.hasRidingEntity()) {
      vl = 0;
    } else if (distanceOfResult.hitVec != null && attackRaytraceMeta.lastHitVec != null && distanceOfResult.hitVec.distanceTo(attackRaytraceMeta.lastHitVec) == 0) {
      vl = 0;
    }
    return vl;
  }

  private boolean processIterativeReachCheck(Player player, WrappedEntity attackedEntity) {
    User user = userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = user.meta().clientData();

    double blockReachDistance = Raytracer.reachDistance(meta);
    boolean hasAlwaysMouseDelayFix = clientData.protocolVersion() >= 314;
    double minReach = findLowestPossibleReachIterative(user, attackedEntity, hasAlwaysMouseDelayFix, false);
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
      if (movementData.hasRidingEntity()) {
        message += " (vehicle)";
      }

      Violation violation = Violation.builderFor(AttackRaytrace.class)
        .forPlayer(player).withMessage(message).withDetails(details)
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

  private double findLowestPossibleReachIterative(
    User user, WrappedEntity entity, boolean hasAlwaysMouseDelayFix, boolean stopOnFound
  ) {
    WrappedEntity clonedEntity = entity.clone();
    Player player = user.player();
    User.UserMeta meta = user.meta();
    UserMetaClientData clientData = meta.clientData();
    boolean alternativePositionY = clientData.protocolVersion() == UserMetaClientData.VER_1_8;
    UserMetaMovementData movementData = meta.movementData();
    float rotationYaw = movementData.rotationYaw % 360;
    float lastRotationYaw = movementData.lastRotationYaw % 360;
    double blockReachDistance = Raytracer.reachDistance(meta);

    double minReach = 10;
    for (WrappedEntity.EntityPositionContext possiblePosition : clonedEntity.positionHistory) {
      clonedEntity.position = possiblePosition.clone();
      // mouse delay fix
      Raytracer.EntityInteractionRaytrace resultWithoutIncrement = Raytracer.distanceOfCombo(
        player,
        clonedEntity,
        alternativePositionY,
        movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
        lastRotationYaw,
        rotationYaw, movementData.rotationPitch,
        0.13f,
        !hasAlwaysMouseDelayFix
      );
      double minReachInItr = resultWithoutIncrement.reach;
      if (stopOnFound && resultWithoutIncrement.reach < blockReachDistance) {
        return resultWithoutIncrement.reach;
      }
      while (clonedEntity.position.newPosRotationIncrements > 0) {
        clonedEntity.onUpdate();
        // mouse delay fix
        Raytracer.EntityInteractionRaytrace result = Raytracer.distanceOfCombo(
          player,
          clonedEntity,
          alternativePositionY,
          movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
          lastRotationYaw,
          rotationYaw, movementData.rotationPitch,
          0.13f,
          !hasAlwaysMouseDelayFix
        );
        if (stopOnFound && result.reach < blockReachDistance) {
          return result.reach;
        }
        minReachInItr = Math.min(minReachInItr, result.reach);
      }
      minReach = Math.min(minReach, minReachInItr);
    }

    // when standing still
    if (movementData.recentlyEncounteredFlyingPacket(1)
      && user.meta().clientData().protocolVersion() >= VER_1_9) {
      for (WrappedEntity.EntityPositionContext possiblePosition : entity.positionHistory) {
        // TODO: 01/07/21 add general packet based length tolerance
        clonedEntity.position = possiblePosition.clone();
        // mouse delay fix
        Raytracer.EntityInteractionRaytrace resultWithoutIncrement = Raytracer.distanceOfCombo(
          player,
          clonedEntity,
          false,
          movementData.positionX, movementData.positionY, movementData.positionZ,
          rotationYaw,
          rotationYaw, movementData.rotationPitch,
          0.13f,
          false
        );
        if (stopOnFound && resultWithoutIncrement.reach < blockReachDistance) {
          return resultWithoutIncrement.reach;
        }
        double minReachInItr = resultWithoutIncrement.reach;

        while (clonedEntity.position.newPosRotationIncrements > 0) {
          clonedEntity.onUpdate();
          // mouse delay fix
          Raytracer.EntityInteractionRaytrace result = Raytracer.distanceOfCombo(
            player,
            clonedEntity,
            false,
            movementData.positionX, movementData.positionY, movementData.positionZ,
            rotationYaw,
            rotationYaw, movementData.rotationPitch,
            0.13f,
            false
          );
          if (stopOnFound && result.reach < blockReachDistance) {
            return result.reach;
          }
          minReachInItr = Math.min(minReachInItr, result.reach);
        }
        minReach = Math.min(minReach, minReachInItr);
      }
    }

    return minReach;
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

  public static class AttackRaytraceMeta extends UserCustomCheckMeta {
    public int lastFlyPacketCounterReach = 0;
    public List<Attack> pendingAttacks = new ArrayList<>();
    public int confidence;
    public WrappedVector lastHitVec;
  }

  public static class Attack {
    private boolean shouldResend;
    private final PacketContainer packet;
    private final int entityId;

    public Attack(PacketContainer packet, int entityId, boolean shouldResend) {
      this.packet = packet;
      this.entityId = entityId;
      this.shouldResend = shouldResend;
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