package de.jpx3.intave.check.combat;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers.EntityUseAction;
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
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.tracker.entity.EntityTracker;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.raytrace.Raytrace;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static com.comphenix.protocol.wrappers.EnumWrappers.EntityUseAction.ATTACK;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOW;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.NORMAL;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DONT_PROCESS_VIOSTAT;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_9;

@Relocate
public final class AttackRaytrace extends MetaCheck<AttackRaytrace.AttackRaytraceMeta> {
  private static final char[] VOCALS = "aeiou".toCharArray();
  private final IntavePlugin plugin;
  private final CheckViolationLevelDecrementer hitboxDecrementer, reachDecrementer;
  private final boolean zeroNetworkTolerance;
  private final double VL_DECREMENT_PER_ATTACK = 0.125;
  private static final int MAX_ALLOWED_PENDING_ATTACKS = 5;

  public AttackRaytrace(IntavePlugin plugin) {
    super("AttackRaytrace", "attackraytrace", AttackRaytraceMeta.class);
    this.plugin = plugin;
    this.hitboxDecrementer = new CheckViolationLevelDecrementer(this, "applicable-thresholds.hitbox", VL_DECREMENT_PER_ATTACK * 0.5);
    this.reachDecrementer = new CheckViolationLevelDecrementer(this, "applicable-thresholds.reach", VL_DECREMENT_PER_ATTACK * 2);
    this.zeroNetworkTolerance = plugin.getConfig().getBoolean("checks.timer.low-tolerance", false) && plugin.getConfig().getBoolean("checks.timer.block-stutter-hits", false);
    // Send a notice message to the server owner if zero tolerance is enabled
    if (zeroNetworkTolerance) {
      IntaveLogger.logger().info("Zero network tolerance enabled");
    }
  }

  @PacketSubscription(
    priority = LOW,
    packetsIn = USE_ENTITY
  )
  public void receiveUseEntityPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    AttackRaytraceMeta meta = metaOf(user);
    AbilityMetadata abilities = user.meta().abilities();
    MovementMetadata movement = user.meta().movement();
    ViolationMetadata violationMeta = user.meta().violationLevel();

    PacketContainer packet = event.getPacket();
    EntityUseReader reader = PacketReaders.readerOf(packet);
    EntityUseAction action = reader.useAction();

    // Only process attacks, interactions should not be checked
    if (action == ATTACK) {
      List<Attack> pendingAttacks = meta.pendingAttacks;
      int entityId = packet.getIntegers().read(0);
      Entity entity = EntityTracker.entityByIdentifier(user, entityId);
      // Allow attacks on invalid entity states
      if (entity == null
        || entity instanceof Entity.Destroyed
        || abilities.unsynchronizedHealth <= 0) {
        // check again?
        reader.release();
        return;
      }
      boolean inTeleport = movement.lastTeleport == 0 || violationMeta.isInActiveTeleportBundle;
      boolean firstRaytraceSuccessful = false;
      if (!inTeleport && !entityInTimeout(user, entity, entity.pendingFeedbackPackets())) {
        // Make a first attempt at ray-tracing to reduce compute time
        Raytrace raytrace = fireRaytraceFor(user, entity, computeExpansionFor(user), true);
        double blockReachDistance = Raytracing.reachDistanceOf(user);
        if (raytrace.reach() <= blockReachDistance) {
          firstRaytraceSuccessful = true;
        }
      }
      boolean resendLater = !firstRaytraceSuccessful;
      if (resendLater) {
        // Cancel attack and redirect it
        if (event.isReadOnly()) {
          event.setReadOnly(false);
        }
        event.setCancelled(true);
      }
      PacketContainer clone = packet.shallowClone();
      Attack attack = new Attack(clone, entityId, resendLater, entity.pendingFeedbackPackets());
      // Only add attack to queue if queue size is small enough
      if (pendingAttacks.size() < MAX_ALLOWED_PENDING_ATTACKS) {
        pendingAttacks.add(attack);
      }
    }
    reader.release();
  }

  @PacketSubscription(
    priority = NORMAL,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK}
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    AttackRaytraceMeta meta = metaOf(user);
    AbilityMetadata abilities = user.meta().abilities();
    MovementMetadata movement = user.meta().movement();
    ProtocolMetadata protocol = user.meta().protocol();
    List<Attack> pendingAttacks = meta.pendingAttacks;
    PacketContainer packet = event.getPacket();
    // Clear attacks if recently teleported
    if (movement.lastTeleport <= 1 || movement.awaitTeleport) {
      pendingAttacks.clear();
    }
    // Apply flying packets (first boolean)
    if (!packet.getBooleans().read(1)) {
      meta.flyingPacketCounter++;
    } else {
      meta.flyingPacketCounter = 0;
    }
    // Process all pending attacks
    for (Attack pendingAttack : pendingAttacks) {
      float entityHealth = abilities.unsynchronizedHealth;
      Entity attackedEntity = EntityTracker.entityByIdentifier(user, pendingAttack.entityId);
      // Once again ignore invalid entity states to make sure nothing is processed wrongly
      if (entityHealth <= 0
        || attackedEntity == null
        || attackedEntity instanceof Entity.Destroyed) {
        return;
      }

      boolean hasNotTimedOut = !entityInTimeout(user, attackedEntity, pendingAttack.pendingFeedbackPackets());
      boolean unsafeSynchronization = movement.dropPostTickMotionProcessing && protocol.protocolVersion() >= 755;
      boolean entityOutOfSync = (!protocol.flyingPacketsAreSent() && movement.receivedFlyingPacketIn(2))
        || !attackedEntity.clientSynchronized || unsafeSynchronization;
      // As entity attack redirections are processed inside this, we don't need to do anything extra to block hits besides
      // just not raytracing
      if (hasNotTimedOut) {
        // This might seem confusing but this is definitely required! DO NOT TINKER
        if (entityOutOfSync) {
          processAttackRaytraceBruteforceFor(user, attackedEntity, pendingAttack);
        } else {
          processAttackRaytraceFor(user, attackedEntity, pendingAttack, computeExpansionFor(user));
        }
      }
    }
    pendingAttacks.clear();
  }

  // Block any latency abusing cheats
  private boolean entityInTimeout(User user, Entity attackedEntity, long pendingFeedbacks) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    ViolationMetadata violations = meta.violationLevel();
    ConnectionMetadata connection = meta.connection();

    int maximumPendingFeedbackPackets = trustFactorSetting("pending-allowance", player) + (int) MathHelper.minmax(1, LatencyStudy.cachedAverage(), 20);
//    long pendingFeedbacks = attackedEntity.pendingFeedbackPackets();
    LatencyStudy.enterHit((short) pendingFeedbacks);

    // protection 1: absolute limit
    boolean entityHasTimedOut = pendingFeedbacks >= maximumPendingFeedbackPackets;

    long transactionPingAverage = connection.transactionPingAverage();
    double transactionTickAverage = transactionPingAverage / 50d;
    int absoluteLimit = zeroNetworkTolerance ? 3 : 12;
    int historyBasedLimit = Math.min((int) ((LatencyStudy.cachedAverage() + transactionTickAverage + 0.5) * 0.6), absoluteLimit);
    boolean pendingOverAverage = transactionPingAverage > 0 && pendingFeedbacks > historyBasedLimit;
    double trustfactorBaseDistanceLimit = trustFactorSetting("pending-distance", player);
    double actualDistance = attackedEntity.immediateDistanceToClientPosition();
    boolean distanceOverLimit = actualDistance > trustfactorBaseDistanceLimit;
    // If something malicious was detected, block the attack
    if (pendingOverAverage || distanceOverLimit) {
      boolean needsToBeBlocked = user.trustFactor().atOrBelow(TrustFactor.RED);
      String message = player.getName() + " attack latency (" + (needsToBeBlocked ? "blocked, " : "") + pendingFeedbacks + "/"
        + historyBasedLimit + "p @" + transactionPingAverage + "ms, " + formatDouble(actualDistance, 2) + "/"
        + formatDouble(trustfactorBaseDistanceLimit, 2) + "blocks)";
      String shortMessage = player.getName() + " attacked " + pendingFeedbacks + " > " + historyBasedLimit + " @ " + transactionPingAverage + "ms";
      MessageSeverity severity = Math.abs(pendingFeedbacks - historyBasedLimit) < 4 ? MessageSeverity.LOW : MessageSeverity.MEDIUM;
      DebugBroadcast.broadcast(player, MessageCategory.ATLALI, severity, message, shortMessage);
      // Block entity hit if required
      if (needsToBeBlocked) {
        // protection 2: environment based limit
        entityHasTimedOut = true;
      }
    }

    // protection 3: short transaction ping based limit
    double multiplier = 0.95;// - ((violations.backtrackVL / 30) * 0.1);
    double shortTransactionPingAverage = connection.shortTransactionPingAverage() / 50d;
    double normalTransactionPingAverage = connection.transactionPingAverage() / 50d;
    double largerTransactionPingAverage = Math.max(shortTransactionPingAverage, normalTransactionPingAverage);

    double unroundedTicksOverLimit = pendingFeedbacks - Math.ceil((largerTransactionPingAverage * multiplier)/* + 0.25*/);
    int ticksOverLimit = (int) Math.floor(unroundedTicksOverLimit);

    if (ticksOverLimit > 0) {
      /*
      attacks are always CLIENT -> SERVER, transaction ping is always SERVER -> CLIENT -> SERVER, so double
      to limit the attack latency, we can assume transaction ping is always 2x the attack latency, so we come with 200% margin
       */
      boolean recentlyIncreased = System.currentTimeMillis() - violations.lastIncreaseBacktrackVL > 800;
      if (recentlyIncreased) {
        violations.backtrackVL += Math.min(3, unroundedTicksOverLimit);
        violations.lastIncreaseBacktrackVL = System.currentTimeMillis();
      } else {
        violations.backtrackVL += Math.min(3, unroundedTicksOverLimit) / 3d;
      }
      if (violations.backtrackVL >= 10 || unroundedTicksOverLimit >= 3) {
        //entityHasTimedOut = true;
      }
      if (violations.backtrackVL > 25) {
        violations.backtrackVL = 30;

        if (recentlyIncreased) {
          String entityName = attackedEntity.entityName();
          Violation violation = Violation.builderFor(AttackRaytrace.class)
            .forPlayer(player).withCustomThreshold("timeout")
            .withVL(0)
            .withMessage("attacked expired position of " + resolveArticle(entityName) + " " + entityName.toLowerCase(Locale.ROOT))
            .withDetails(pendingFeedbacks + "t attack, " + formatDouble(shortTransactionPingAverage, 2) + "*" + formatDouble(multiplier, 2) + "t latency")
            .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
            .build();
          ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
          double after = violationContext.violationLevelAfter();
          if (after > 10) {
            user.nerf(AttackNerfStrategy.HT_SPOOF, "internal");
          }
        }
      }
//      Synchronizer.synchronize(() -> {
//        player.sendMessage("BacktrackVL: " + violations.backtrackVL + " multiplier: " + formatDouble(multiplier, 2));
//      });
    } else if (violations.backtrackVL > 0) {
      if (System.currentTimeMillis() - violations.lastIncreaseBacktrackVL > 20 * 1000) {
        violations.backtrackVL = Math.max(0, violations.backtrackVL - 5);
        violations.lastIncreaseBacktrackVL = System.currentTimeMillis();
      }
      violations.backtrackVL -= 0.05;
    }

    return entityHasTimedOut;
  }

  /**
   * Processes the reach check 3x for all possible entity and player positions (Interpolation in
   * client is 3 ticks long). Takes the lowest reach calculated as a result of the calculation.
   *
   * <p>This is required when we don't know the exact position of the entity as the player either
   * didn't send flying packets or it's not synchronized yet
   *
   * @param user   The user which attacked
   * @param entity The attacked entity
   * @param attack The current attack
   * @since 14.5.8
   */
  private void processAttackRaytraceBruteforceFor(User user, Entity entity, Attack attack) {
    MetadataBundle meta = user.meta();
    Raytrace lowestRaytrace = fireRaytraceFor(user, entity, 0.13f, false);
    double blockReachDistance = Raytracing.reachDistanceOf(meta);
    // Iteratively find out reach if ray-trace wasn't valid
    if (lowestRaytrace.reach() > blockReachDistance) {
      double reach = findLowestPossibleReachIterative(user, entity);
      // We don't use the positions here anyway, just fill them with empty ones
      Position emptyPosition = new Position(0, 0, 0);
      lowestRaytrace = new Raytrace(emptyPosition, emptyPosition, reach);
    }
    processResult(user, lowestRaytrace, entity, attack, 0.13f, true);
  }

  /**
   * Takes in multiple factors to calculate the maximum possible reach of the player using the
   * previous positions of the entity, this is required for synchronized entities or if the player
   * is uncertain
   *
   * <p>Iteratively checks with both the previous and current player position to ensure false
   * positives are eliminated
   *
   * @param user   The user to check for
   * @param entity The entity which was attacked by the user
   * @return The maximum reach possible
   * @since 14.6.0
   */
  private double findLowestPossibleReachIterative(User user, Entity entity) {
    MetadataBundle meta = user.meta();
    MovementMetadata movement = meta.movement();
    double blockReachDistance = Raytracing.reachDistanceOf(meta);
    double minReach = findLowestPossibleReachIterative(user, entity, false);
    // Stop if reach is already lower than block reach distance to save performance
    if (minReach < blockReachDistance) {
      return minReach;
    }
    // Flying packets missing on 1.19+
    if (movement.receivedFlyingPacketIn(1) && user.protocolVersion() >= VER_1_9) {
      double reach = findLowestPossibleReachIterative(user, entity, true);
      minReach = Math.min(minReach, reach);
    }
    return minReach;
  }

  /**
   * Calculates the highest possible reach using previous entity positions
   *
   * @param user   The user to check for
   * @param entity The entity which was attacked by the user
   * @return The maximum reach possible
   * @since 14.6.0
   */
  private double findLowestPossibleReachIterative(
    User user, Entity entity, boolean currentPosition) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    List<Entity.EntityPositionContext> history = entity.positionHistory;
    int maximumPendingFeedbackPackets =
      trustFactorSetting("pending-allowance", player)
        + (int) MathHelper.minmax(0, LatencyStudy.cachedAverage(), 20);
    double blockReachDistance = Raytracing.reachDistanceOf(meta);
    double minReach = 10;
    boolean livingEntity = entity.typeData().isLivingEntity();
    int from = history.size() - 1;
    for (int i = from; i >= 0; i--) {
      // If current position exceeds maximum pending packets skip this entity tick
      if (from - i > maximumPendingFeedbackPackets) {
        continue;
      }
      Entity.EntityPositionContext possiblePosition = history.get(i);
      entity.position = possiblePosition.clone();
      Raytrace resultWithoutIncrement = fireRaytraceFor(user, entity, 0.13f, currentPosition);
      // Stop if a valid reach was found
      if (resultWithoutIncrement.reach() < blockReachDistance) {
        return resultWithoutIncrement.reach();
      }
      double minReachInItr = resultWithoutIncrement.reach();
      int limit = 5;
      while (entity.position.newPosRotationIncrements > 0 && livingEntity) {
        // If limit exceeded stop to save performance
        if (limit-- <= 0) {
          break;
        }
        entity.onUpdate();
        Raytrace result = fireRaytraceFor(user, entity, 0.13f, currentPosition);
        // Stop if a valid reach was found
        if (result.reach() < blockReachDistance) {
          return result.reach();
        }
        minReachInItr = Math.min(minReachInItr, result.reach());
      }
      minReach = Math.min(minReach, minReachInItr);
    }
    return minReach;
  }

  /**
   * Processes the reach check for a given user
   *
   * @param user      The user which attacked
   * @param entity    The attacked entity
   * @param attack    The current attack
   * @param expansion The hit-box expansion applied for the player (this differs depending on the
   *                  client)
   * @since 14.5.8
   */
  private void processAttackRaytraceFor(User user, Entity entity, Attack attack, float expansion) {
    Raytrace raytrace = fireRaytraceFor(user, entity, expansion, false);
    processResult(user, raytrace, entity, attack, expansion, false);
  }

  /**
   * Processes the raytrace result and creates violations from it if calculations exceed legit
   * values
   *
   * @param user      The user to process the raytrace for
   * @param raytrace  The raytrace
   * @param attacked  The attacked entity
   * @param attack    The attack to be processed
   * @param expansion The hit-box expansion used while raytracing
   * @param estimated Whether the raytrace was estimated or not (will not give vl if it is)
   * @since 14.5.8
   */
  private void processResult(
    User user, Raytrace raytrace,
    Entity attacked, Attack attack,
    float expansion, boolean estimated
  ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    ViolationMetadata violationMeta = meta.violationLevel();
    String entityName = attacked.entityName();
    double blockReachDistance = Raytracing.reachDistanceOf(meta);
    RaytraceResult result = RaytraceResult.of(raytrace, blockReachDistance);
    int vl = calculateVlFor(user, raytrace, result, attacked, expansion, estimated);
    String estimationSuffix = estimated ? " (estimated)" : "";
    String message, details, thresholdKey, sibyl;
    double reach = 0;
    boolean resendAllowed = attack.shouldResend() && !violationMeta.isInActiveTeleportBundle;
    switch (result) {
      case MISS: {
        message = String.format(
          "attacked %s %s out of sight %s",
          resolveArticle(entityName), entityName.toLowerCase(), estimationSuffix
        );
        details = "";
        thresholdKey = "applicable-thresholds.hitbox";
        sibyl = String.format(
          "%s/%d missed hit on %s",
          player.getName(), user.protocolVersion(), entityName.toLowerCase()
        );
        reach = 10;
        break;
      }
      case REACH: {
        String displayReach = formatDouble(raytrace.reach(), 4);
        message = String.format(
          "attacked %s %s from too far away %s",
          resolveArticle(entityName), entityName.toLowerCase(), estimationSuffix
        );
        details = displayReach + " blocks";
        thresholdKey = "applicable-thresholds.reach";
        sibyl = String.format(
          "%s/%d attacked %s from %s",
          player.getName(), user.protocolVersion(), entityName.toLowerCase(), displayReach
        );
        reach = raytrace.reach();
        break;
      }
      default: {
        hitboxDecrementer.decrement(user, VL_DECREMENT_PER_ATTACK);
        reachDecrementer.decrement(user, VL_DECREMENT_PER_ATTACK);
        if (raytrace.reach() > 2.8 && user.meta().punishment().nerferOfType(AttackNerfStrategy.CANCEL_FIRST_HIT).active()) {
          resendAllowed = false;
        }
        // Redirect if resend is allowed
        if (resendAllowed) {
          redirectValidPacket(player, attack.packet());
        }
        return;
      }
    }
    DebugBroadcast.broadcast(player, MessageCategory.ATRAFLT, MessageSeverity.HIGH, sibyl, sibyl);
    Violation violation = Violation.builderFor(AttackRaytrace.class)
      .forPlayer(player).withMessage(message).withDetails(details)
      .withCustomThreshold(thresholdKey).withVL(vl)
      .withPlaceholder("reach", formatDouble(reach, 4))
      .appendFlags(estimated ? DONT_PROCESS_VIOSTAT : 0)
      .build();
    ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
    // Apply damage cancel after 50 VL
    if (violationContext.violationLevelAfter() > 50 && !estimated) {
      // dmc3
      user.nerf(AttackNerfStrategy.CRITICALS, "3");
      user.nerf(AttackNerfStrategy.BURN_LONGER, "3");
      user.nerf(AttackNerfStrategy.BLOCKING, "3");
    }
    // Allow attack if player has bypassing trust-factor
    if (user.trustFactor().atLeast(TrustFactor.BYPASS)) {
      if (resendAllowed) {
        redirectValidPacket(player, attack.packet());
      }
      statisticApply(user, CheckStatistics::increasePasses);
    }
  }

  /**
   * Redirects a validated attack to the server
   *
   * @param player The player to redirect the packet for
   * @param packet The packet to redirect
   * @since 14.6.0
   */
  private void redirectValidPacket(Player player, PacketContainer packet) {
    userOf(player).ignoreNextInboundPacket();
    PacketSender.receiveClientPacketFrom(player, packet);
  }

  /**
   * Computes violation points for an evaluated {@link Raytrace} which will get applied to a {@link
   * Player}
   *
   * @param user      The user to compute violation points for
   * @param raytrace  The raytrace
   * @param result    The raytrace result
   * @param attacked  The attacked entity
   * @param expansion The hit-box expansion used
   * @param estimated Whether the raytrace was estimated or not
   * @return The computed violation points
   * @since 14.5.8
   */
  private int calculateVlFor(
    User user, Raytrace raytrace,
    RaytraceResult result, Entity attacked,
    float expansion, boolean estimated
  ) {
    AttackRaytraceMeta attackRaytraceMeta = metaOf(user);
    Position targetPosition = raytrace.targetPosition();
    boolean invalidRaytrace = targetPosition != null
      && attackRaytraceMeta.lastPosition != null
      && targetPosition.distance(attackRaytraceMeta.lastPosition) == 0;
    if (user.meta().movement().isInVehicle()) {
      invalidRaytrace = true;
    }
    // Do not apply violation points if the raytrace was estimated or invalid
    if (estimated || invalidRaytrace) {
      return 0;
    }
    int vl = result.baseVLForAttack(attacked);
    // Reduce vl if hit-box was enlarged due to flying packets
    if (expansion > 0.1f) {
      vl /= 2f;
    }
    attackRaytraceMeta.lastPosition = raytrace.targetPosition();
    return vl;
  }

  /**
   * Fires an entity raytrace for the given user
   *
   * @param user            The user
   * @param entity          The entity
   * @param expansion       The hit-box expansion
   * @param currentPosition Defines whether the current or past position should be used
   * @return The raytrace result
   * @since 14.5.8
   */
  private Raytrace fireRaytraceFor(
    User user, Entity entity, float expansion, boolean currentPosition
  ) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();

    double x = currentPosition ? movementData.positionX : movementData.lastPositionX;
    double y = currentPosition ? movementData.positionY : movementData.lastPositionY;
    double z = currentPosition ? movementData.positionZ : movementData.lastPositionZ;
    boolean requiresAlternativeY = clientData.flyingPacketsAreSent();
    boolean fixedMouseDelay = clientData.protocolVersion() >= 314;
    float yaw = movementData.rotationYaw % 360f;
    float lastYaw = movementData.lastRotationYaw % 360f;

    return Raytracing.doubleMDFBlockConstraintEntityRaytrace(
      user.player(),
      entity,
      requiresAlternativeY,
      x, y, z,
      lastYaw, yaw,
      movementData.rotationPitch,
      expansion, !fixedMouseDelay
    );
  }

  /**
   * Computes the hit box expansion for the player
   *
   * @param user The user which is used to compute the expansion
   * @return The expansion
   */
  private float computeExpansionFor(User user) {
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();
    ConnectionMetadata connection = meta.connection();
    AttackRaytraceMeta attackRaytraceMeta = metaOf(user);
    // Process 1.8 and lower
    if (clientData.flyingPacketsAreSent()) {
      return attackRaytraceMeta.flyingPacketCounter > 0 ? 0.13f : 0.1f;
    } else {
      return connection.lastCCCInfoMessageSent > 0 ? 0.1f : 0f;
    }
  }

  /**
   * Resolves what article to use for a given entity name
   *
   * @param entityName The entity name
   * @return The article
   * @since 14.5.8
   */
  private String resolveArticle(String entityName) {
    char c = entityName.trim().toLowerCase(Locale.ROOT).toCharArray()[0];
    boolean isVocal = false;
    for (char vocal : VOCALS) {
      if (vocal == c) {
        isVocal = true;
        break;
      }
    }
    return isVocal ? "an" : "a";
  }

  /**
   * The custom check meta for the {@link AttackRaytrace} check
   *
   * @since 14.5.8
   */
  public static class AttackRaytraceMeta extends CheckCustomMetadata {
    public int flyingPacketCounter = 0;
    public List<Attack> pendingAttacks = new ArrayList<>();
    public Position lastPosition;
  }

  /**
   * The attack stored to be processed after a movement packet was sent by the client
   *
   * @since 14.5.8
   */
  public static class Attack {
    private final boolean shouldResend;
    private final PacketContainer packet;
    private final int entityId;
    private final long pendingFeedbackPackets;

    public Attack(PacketContainer packet, int entityId, boolean shouldResend, long pendingFeedbackPackets) {
      this.packet = packet;
      this.entityId = entityId;
      this.shouldResend = shouldResend;
      this.pendingFeedbackPackets = pendingFeedbackPackets;
    }

    public PacketContainer packet() {
      return packet;
    }

    public int entityId() {
      return entityId;
    }

    public long pendingFeedbackPackets() {
      return pendingFeedbackPackets;
    }

    public boolean shouldResend() {
      return shouldResend;
    }
  }

  /**
   * Used to evaluate a {@link Raytrace} for applying violation levels to a {@link Player}
   *
   * @author Lennox
   * @since 14.5.8
   */
  public enum RaytraceResult {
    VALID(e -> 0),
    REACH(e -> 20),
    MISS(e -> e != null && e.typeData().isLivingEntity() ? 4 : 0);

    private final Function<Entity, Integer> entityToVLIncrease;

    RaytraceResult(Function<Entity, Integer> entityToVLIncrease) {
      this.entityToVLIncrease = entityToVLIncrease;
    }

    public int baseVLForAttack(Entity attacked) {
      return entityToVLIncrease.apply(attacked);
    }

    /**
     * Evaluates a {@link RaytraceResult} based off a given {@link Raytrace} and block reach limit
     *
     * @param raytrace The raytrace
     * @param limit    The reach limit
     * @return The result
     * @since 14.5.8
     */
    public static RaytraceResult of(Raytrace raytrace, double limit) {
      double reach = raytrace.reach();
      if (reach == 10) {
        return MISS;
      } else if (reach > limit) {
        return REACH;
      } else {
        return VALID;
      }
    }
  }
}