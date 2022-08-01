package de.jpx3.intave.check.combat;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.check.CheckStatistics;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.diagnostic.LatencyStudy;
import de.jpx3.intave.diagnostic.message.DebugBroadcast;
import de.jpx3.intave.diagnostic.message.MessageCategory;
import de.jpx3.intave.diagnostic.message.MessageSeverity;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.EntityShade;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.raytrace.Raytrace;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.tracker.entity.EntityTracker.entityByIdentifier;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DONT_PROCESS_VIOSTAT;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_9;

@Relocate
public final class AttackRaytrace extends MetaCheck<AttackRaytrace.AttackRaytraceMeta> {
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
    MetadataBundle meta = user.meta();
    ViolationMetadata violationLevelData = meta.violationLevel();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    AbilityMetadata abilityData = meta.abilities();

    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
    if (action == null) {
      action = packet.getEnumEntityUseActions().read(0).getAction();
    }
    if (action == EnumWrappers.EntityUseAction.ATTACK) {
      PacketContainer packetClone = packet.deepClone();
      int entityId = packet.getIntegers().read(0);
      EntityShade entity = entityByIdentifier(user, entityId);
      boolean checkAgain;
      float unsynchronizedHealth = abilityData.unsynchronizedHealth;
      if (entity == null || entity instanceof EntityShade.Destroyed || unsynchronizedHealth <= 0) {
        checkAgain = true;
      } else {
        if (movementData.lastTeleport == 0 || violationLevelData.isInActiveTeleportBundle) {
          checkAgain = true;
        } else {
          if ((entity.clientSynchronized && !movementData.recentlyEncounteredFlyingPacket(2) && attackRaytraceMeta.lastFlyPacketCounterReach > 1)
            || clientData.protocolVersion() == ProtocolMetadata.VER_1_8) {
            checkAgain = invalidReachWalking(user, entity);
          } else {
            checkAgain = invalidReachStanding(user, entity);
          }
        }
      }
      if (checkAgain) {
        // Ja, das muss hier hin
        if (event.isReadOnly()) {
          event.setReadOnly(false);
        }
        event.setCancelled(true);
      }
      Attack attack = new Attack(packetClone, entityId, checkAgain);
      List<Attack> pendingAttacks = attackRaytraceMeta.pendingAttacks;
      if (pendingAttacks.size() < 4) {
        pendingAttacks.add(attack);
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
    MetadataBundle meta = user.meta();
    ProtocolMetadata protocolMetadata = meta.protocol();
    MovementMetadata movementData = meta.movement();
    ViolationMetadata violationLevelData = meta.violationLevel();
    if (movementData.lastTeleport == 0) {
      attackRaytraceMeta.pendingAttacks.clear();
      return;
    }

    // make player trustfactor dependent
    int maximumPendingFeedbackPackets = trustFactorSetting("pending-allowance", player) + (int) MathHelper.minmax(0, LatencyStudy.cachedAverage(), 20);

    List<Attack> remainingAttacks = attackRaytraceMeta.pendingAttacks;
    for (Attack remainingAttack : remainingAttacks) {
      statisticApply(user, CheckStatistics::increaseTotal);
      EntityShade entity = entityByIdentifier(user, remainingAttack.entityId());

      Boolean cancelHit = null;
      AbilityMetadata abilityData = user.meta().abilities();
      float unsynchronizedHealth = abilityData.unsynchronizedHealth;

      // bypass when the entity is null or on entities which are riding and players which are mounted on entities
      if (entity != null) {
        long pendingFeedbackPackets = entity.pendingFeedbackPackets();
//        player.sendMessage(String.valueOf(pendingFeedbackPackets));

        LatencyStudy.enterHit((short) pendingFeedbackPackets);

        // stops raytrace if the entity is null or the player is in the death screen
        boolean entityIsAlive = unsynchronizedHealth > 0 && !(entity instanceof EntityShade.Destroyed);
        boolean entityHasNotTimedOut = pendingFeedbackPackets < maximumPendingFeedbackPackets;
        long transactionPingAverage = user.meta().connection().transactionPingAverage();
        double transactionTickAverage = transactionPingAverage / 50d;
        int historyBasedTransactionLimit = (int) ((LatencyStudy.cachedAverage() + transactionTickAverage + 1) * 0.9);
        boolean pendingOverAverage = transactionPingAverage > 0 && pendingFeedbackPackets > historyBasedTransactionLimit;
        boolean blocked = !user.trustFactor().atLeast(TrustFactor.ORANGE);

        if (pendingOverAverage) {
          String message = player.getName() + " attack latency (" + (blocked ? "blocked, " : "") + pendingFeedbackPackets + "/" + historyBasedTransactionLimit + "p with " + transactionPingAverage + "ms tra-ping, " + entity.immediateDistanceToClientPosition() + " dist)";
          String shortMessage = player.getName() + " attacked " + pendingFeedbackPackets + " > " + historyBasedTransactionLimit + " @ " + transactionPingAverage + "ms";
          MessageSeverity severity = Math.abs(pendingFeedbackPackets - historyBasedTransactionLimit) < 4 ? MessageSeverity.LOW : MessageSeverity.MEDIUM;
          DebugBroadcast.broadcast(player, MessageCategory.ATLALI, severity, message, shortMessage);
          if (blocked) {
            entityHasNotTimedOut = false;
          }
        }

        if (entityIsAlive && entityHasNotTimedOut) {
          if (entity.mountedEntity() == null && !player.isInsideVehicle() && entity.typeData().isLivingEntity() && !abilityData.ignoringMovementPackets()) {
            if (!protocolMetadata.flyingPacketStream()/*protocolMetadata.protocolVersion() >= VER_1_9 || protocolMetadata.outdatedClient()*/) {
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
          cancelHit = true;
        }
      } else {
        String attackedNullEntityMessage = player.getName() + " attacked a null entity";
        if (IntaveControl.DISABLE_LICENSE_CHECK) {
          IntaveLogger.logger().error(attackedNullEntityMessage);
        }

        DebugBroadcast.broadcast(player, MessageCategory.ATRAFLT, MessageSeverity.MEDIUM, attackedNullEntityMessage, attackedNullEntityMessage);
//        Synchronizer.synchronize(new Runnable() {
//          @Native
//          @Override
//          public void run() {
//            for (Player authenticatedPlayer : MessageChannelSubscriptions.sibylReceiver()) {
//              if (plugin.sibylIntegrationService().isAuthenticated(authenticatedPlayer)) {
//                String message;
//                message = ChatColor.RED + "[R] " + player.getName() + " attacked a null entity";
//                authenticatedPlayer.sendMessage(message);
//              }
//            }
//          }
//        });
      }
      if (cancelHit == null || !cancelHit || user.trustFactor().atLeast(TrustFactor.BYPASS)) {
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
    userOf(player).ignoreNextInboundPacket();
    PacketSender.receiveClientPacketFrom(player, packet);
  }

  private boolean invalidReachStanding(User user, EntityShade entity) {
    Player player = user.player();
    int maximumPendingFeedbackPackets = trustFactorSetting("pending-allowance", player) + (int) MathHelper.minmax(0, LatencyStudy.cachedAverage(), 20);
    long pendingFeedbackPackets = entity.pendingFeedbackPackets();
    boolean entityHasNotTimedOut = pendingFeedbackPackets < maximumPendingFeedbackPackets;
    if (!entityHasNotTimedOut && entity.clientSynchronized) {
      return true;
    }
    double minReach = findLowestPossibleReachIterative(user, entity, false, true);
    double blockReachDistance = Raytracing.reachDistance(player);
    return minReach > blockReachDistance;
  }

  private boolean invalidReachWalking(User user, EntityShade entity) {
    MovementMetadata movementData = user.meta().movement();
    Player player = user.player();
    double blockReachDistance = Raytracing.reachDistance(player);
    float rotationYaw = movementData.rotationYaw % 360;

    int maximumPendingFeedbackPackets = trustFactorSetting("pending-allowance", player) + (int) MathHelper.minmax(0, LatencyStudy.cachedAverage(), 20);
    long pendingFeedbackPackets = entity.pendingFeedbackPackets();
    boolean entityHasNotTimedOut = pendingFeedbackPackets < maximumPendingFeedbackPackets;

    if (!entityHasNotTimedOut && entity.clientSynchronized) {
      return true;
    }

    // mouse delay fix
    Raytrace distanceOfResult = Raytracing.doubleMDFBlockConstraintEntityRaytrace(
      player,
      entity, true,
      movementData.positionX, movementData.positionY, movementData.positionZ,
      rotationYaw,
      rotationYaw, movementData.rotationPitch,
      0.1f,
      false
    );

    return distanceOfResult.reach() > blockReachDistance;
  }

  /**
   * @param expandHitbox should be "0.1f" for a default hitbox
   */
  private boolean processReachCheck(Player player, EntityShade entity, double expandHitbox) {
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    AttackRaytraceMeta attackRaytraceMeta = metaOf(user);
    AttackMetadata attackData = meta.attack();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    PunishmentMetadata punishmentData = meta.punishment();

    double blockReachDistance = Raytracing.reachDistance(meta);
    boolean alternativePositionY = clientData.protocolVersion() == ProtocolMetadata.VER_1_8;
    boolean hasAlwaysMouseDelayFix = clientData.protocolVersion() >= 314;
    float rotationYaw = movementData.rotationYaw % 360f;
    float lastRotationYaw = movementData.lastRotationYaw % 360f;

    // mouse delay fix
    Raytrace raytrace = Raytracing.doubleMDFBlockConstraintEntityRaytrace(
      player,
      entity, alternativePositionY,
      movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
      lastRotationYaw,
      rotationYaw, movementData.rotationPitch,
      expandHitbox,
      !hasAlwaysMouseDelayFix
    );

    attackData.setLastReach(raytrace.reach());
    String message, details, thresholdKey, special;
    AttackRaytraceResult attackRaytraceResult = AttackRaytrace.AttackRaytraceResult.of(raytrace.reach(), blockReachDistance);
    int vl = applicableViolationPoints(attackRaytraceResult, raytrace, entity, user, expandHitbox);
    String entityName = entity.entityName();

    double reach = 0;

    switch (attackRaytraceResult) {
      case MISS: {
        message = "attacked " + resolveArticle(entityName) + " " + entityName.toLowerCase() + " out of sight";
        details = "";
        thresholdKey = "applicable-thresholds.hitbox";
        special = player.getName() + " missed hit on " + entityName.toLowerCase();
        reach = -1;
        break;
      }
      case REACH: {
        String displayReach = MathHelper.formatDouble(raytrace.reach(), 4);
        message = "attacked " + resolveArticle(entityName) + " " + entityName.toLowerCase() + " from too far away";
        details = displayReach + " blocks";
        thresholdKey = "applicable-thresholds.reach";
        special = player.getName() + " attacked " + entityName.toLowerCase() + " from " + displayReach;
        reach = raytrace.reach();
        break;
      }
      default: {
        hitboxDecrementer.decrement(user, VL_DECREMENT_PER_ATTACK);
        reachDecrementer.decrement(user, VL_DECREMENT_PER_ATTACK);
        if (punishmentData.nerferOfType(AttackNerfStrategy.CANCEL_FIRST_HIT).active()) {
          double moved = Hypot.fast(movementData.motionX(), movementData.motionZ());
          return moved > 0.1 && raytrace.reach() > 2.8;
        }
        return false;
      }
    }

//    Synchronizer.synchronize(new Runnable() {
//      @Native
//      @Override
//      public void run() {
//
//      }
//    });
//    SibylBroadcast.broadcast(special);
    DebugBroadcast.broadcast(player, MessageCategory.ATRAFLT, MessageSeverity.HIGH, special, special);

//    player.sendMessage(attackRaytraceResult + " " + raytrace.reach);

    attackRaytraceMeta.lastHitPosition = raytrace.targetPosition();
//    if (movementData.inVehicle()) {
//      message += " (vehicle)";
//    }

//    if (entity.verifiedPosition) {
//      message += " (verified)";
//    }

    Violation violation = Violation.builderFor(AttackRaytrace.class)
      .forPlayer(player).withMessage(message).withDetails(details)
      .withCustomThreshold(thresholdKey).withVL(vl)
      .withPlaceholder("reach", MathHelper.formatDouble(reach, 4))
      .build();
    ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
    if (violationContext.violationLevelAfter() > 50) {
      //dmc3
      user.applyAttackNerfer(AttackNerfStrategy.DMG_MEDIUM, "3");
    }
    return true;
  }

  private int applicableViolationPoints(
    AttackRaytraceResult attackRaytraceResult,
    Raytrace raytrace,
    EntityShade entity,
    User user, double expandHitbox
  ) {
    AttackRaytraceMeta attackRaytraceMeta = metaOf(user);
    MovementMetadata movementData = user.meta().movement();
    int vl = 0;
    switch (attackRaytraceResult) {
      case MISS: {
        if (!entity.typeData().isLivingEntity()) {
          vl = 0;
        } else {
          vl = 4;
        }
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
      vl *= 1.25;
    }
    if (movementData.isInVehicle()) {
      vl = 0;
    } else if (raytrace.targetPosition() != null && attackRaytraceMeta.lastHitPosition != null && raytrace.targetPosition().distance(attackRaytraceMeta.lastHitPosition) == 0) {
      vl = 0;
    }
    return vl;
  }

  private boolean processIterativeReachCheck(Player player, EntityShade attackedEntity) {
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = user.meta().protocol();

    double blockReachDistance = Raytracing.reachDistance(meta);
    boolean hasAlwaysMouseDelayFix = clientData.protocolVersion() >= 314;
    double minReach = findLowestPossibleReachIterative(user, attackedEntity, hasAlwaysMouseDelayFix, false);
    // TODO: 01/07/21 clear after last possible position
    if (minReach > blockReachDistance) {
      String entityName = attackedEntity.entityName();
      String targetDescriptor = resolveArticle(entityName) + " " + entityName.toLowerCase();
      String thresholdKey;
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
      if (movementData.isInVehicle()) {
        message += " (vehicle)";
      }
      Violation violation = Violation.builderFor(AttackRaytrace.class)
        .forPlayer(player).withMessage(message).withDetails(details)
        .withCustomThreshold(thresholdKey).withVL(0)
        .appendFlags(DONT_PROCESS_VIOSTAT)
        .build();
      Modules.violationProcessor().processViolation(violation);
      return true;
    }
    hitboxDecrementer.decrement(user, VL_DECREMENT_PER_ATTACK);
    reachDecrementer.decrement(user, VL_DECREMENT_PER_ATTACK);
    return false;
  }

  private double findLowestPossibleReachIterative(
    User user, EntityShade entity, boolean enforceMouseDelayFix, boolean stopOnFound
  ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();
    boolean alternativePositionY = clientData.protocolVersion() == ProtocolMetadata.VER_1_8;
    MovementMetadata movementData = meta.movement();
    float rotationYaw = movementData.rotationYaw % 360;
    float lastRotationYaw = movementData.lastRotationYaw % 360;
    float rotationPitch = movementData.rotationPitch;
    double positionX = movementData.positionX;
    double positionY = movementData.positionY;
    double positionZ = movementData.positionZ;
    double lastPositionX = movementData.lastPositionX;
    double lastPositionY = movementData.lastPositionY;
    double lastPositionZ = movementData.lastPositionZ;
    double blockReachDistance = Raytracing.reachDistance(meta);
    int maximumPendingFeedbackPackets = trustFactorSetting("pending-allowance", player) + (int) MathHelper.minmax(0, LatencyStudy.cachedAverage(), 20);
    double minReach = 10;
    EntityShade clonedEntity = entity.temporaryCopy();
    boolean livingEntity = entity.typeData().isLivingEntity();
    List<EntityShade.EntityPositionContext> positionHistory = clonedEntity.positionHistory;
    int from = positionHistory.size() - 1;
    for (int i = from; i >= 0; i--) {
      if (from - i > maximumPendingFeedbackPackets) {
        continue;
      }
      EntityShade.EntityPositionContext possiblePosition = positionHistory.get(i);
      clonedEntity.position = possiblePosition.clone();
      // mouse delay fix
      Raytrace resultWithoutIncrement = Raytracing.doubleMDFBlockConstraintEntityRaytrace(
        player,
        clonedEntity,
        alternativePositionY,
        lastPositionX, lastPositionY, lastPositionZ,
        lastRotationYaw,
        rotationYaw, rotationPitch,
        0.13f,
        !enforceMouseDelayFix
      );
      double minReachInItr = resultWithoutIncrement.reach();
      if (stopOnFound && resultWithoutIncrement.reach() < blockReachDistance) {
        return resultWithoutIncrement.reach();
      }
      int limit = 5;
      while (clonedEntity.position.newPosRotationIncrements > 0 && livingEntity) {
        if (limit-- <= 0) {
          break;
        }
        clonedEntity.onUpdate();
        // mouse delay fix
        Raytrace result = Raytracing.doubleMDFBlockConstraintEntityRaytrace(
          player,
          clonedEntity,
          alternativePositionY,
          lastPositionX, lastPositionY, lastPositionZ,
          lastRotationYaw,
          rotationYaw, rotationPitch,
          0.13f,
          !enforceMouseDelayFix
        );
        if (stopOnFound && result.reach() < blockReachDistance) {
          return result.reach();
        }
        minReachInItr = Math.min(minReachInItr, result.reach());
      }
      minReach = Math.min(minReach, minReachInItr);
    }

    // when standing still
    if (movementData.recentlyEncounteredFlyingPacket(1)
      && user.protocolVersion() >= VER_1_9) {
      List<EntityShade.EntityPositionContext> history = entity.positionHistory;
      from = history.size() - 1;
      for (int i = from; i >= 0; i--) {
        if (from - i > maximumPendingFeedbackPackets) {
          continue;
        }
        EntityShade.EntityPositionContext possiblePosition = history.get(i);
        // TODO: 01/07/21 add general packet based length tolerance
        clonedEntity.position = possiblePosition.clone();
        // mouse delay fix
        Raytrace resultWithoutIncrement = Raytracing.doubleMDFBlockConstraintEntityRaytrace(
          player,
          clonedEntity,
          false,
          positionX, positionY, positionZ,
          rotationYaw,
          rotationYaw, rotationPitch,
          0.13f,
          false
        );
        if (stopOnFound && resultWithoutIncrement.reach() < blockReachDistance) {
          return resultWithoutIncrement.reach();
        }
        double minReachInItr = resultWithoutIncrement.reach();

        int limit = 5;
        while (clonedEntity.position.newPosRotationIncrements > 0 && livingEntity) {
          if (limit-- <= 0) {
            break;
          }
          clonedEntity.onUpdate();
          // mouse delay fix
          Raytrace result = Raytracing.doubleMDFBlockConstraintEntityRaytrace(
            player,
            clonedEntity,
            false,
            positionX, positionY, movementData.positionZ,
            rotationYaw,
            rotationYaw, rotationPitch,
            0.13f,
            false
          );
          if (stopOnFound && result.reach() < blockReachDistance) {
            return result.reach();
          }
          minReachInItr = Math.min(minReachInItr, result.reach());
        }
        minReach = Math.min(minReach, minReachInItr);
      }
    }

    return minReach;
  }

  private static final char[] vocals = "aeiou".toCharArray();

  private String resolveArticle(String entityName) {
    char c = entityName.trim().toLowerCase(Locale.ROOT).toCharArray()[0];
    boolean isVocal = false;
    for (char vocal : vocals) {
      if (vocal == c) {
        isVocal = true;
        break;
      }
    }
    return isVocal ? "an" : "a";
  }

  public static class AttackRaytraceMeta extends CheckCustomMetadata {
    public int lastFlyPacketCounterReach = 0;
    public List<Attack> pendingAttacks = new ArrayList<>();
    public int confidence;
    public Position lastHitPosition;
  }

  public static class Attack {
    private final boolean shouldResend;
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