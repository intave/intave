package de.jpx3.intave.check.combat;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import de.jpx3.intave.version.MinecraftVersion;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.check.CheckStatistics;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.diagnostic.LatencyStudy;
import de.jpx3.intave.diagnostic.message.DebugBroadcast;
import de.jpx3.intave.diagnostic.message.MessageCategory;
import de.jpx3.intave.diagnostic.message.MessageSeverity;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.FeedbackAnalysis;
import de.jpx3.intave.module.feedback.FeedbackAnalysis.FeedbackAnalysisMeta.LatencyInfo;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.tracker.entity.EntityTracker;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.share.FriendlyByteBuf;
import de.jpx3.intave.share.HistoryWindow;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.raytrace.Raytrace;
import de.jpx3.intave.world.raytrace.Raytracing;
import io.netty.buffer.ByteBuf;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOW;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.NORMAL;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DONT_PROCESS_VIOSTAT;

public final class AttackRaytrace extends MetaCheck<AttackRaytrace.AttackRaytraceMeta> {
  private static final char[] VOCALS = "aeiou".toCharArray();
  private final IntavePlugin plugin;
  private final CheckViolationLevelDecrementer hitboxDecrementer, reachDecrementer, timeoutDecrementer;
  private final boolean zeroNetworkTolerance;
  private final double VL_DECREMENT_PER_ATTACK = 0.125;
  private static final int MAX_ALLOWED_PENDING_ATTACKS = 10;

  public AttackRaytrace(IntavePlugin plugin) {
    super("AttackRaytrace", "attackraytrace", AttackRaytraceMeta.class);
    this.plugin = plugin;
    this.hitboxDecrementer = new CheckViolationLevelDecrementer(this, "applicable-thresholds.hitbox", VL_DECREMENT_PER_ATTACK * 0.5);
    this.reachDecrementer = new CheckViolationLevelDecrementer(this, "applicable-thresholds.reach", VL_DECREMENT_PER_ATTACK * 2);
    this.timeoutDecrementer = new CheckViolationLevelDecrementer(this, "applicable-thresholds.timeout", VL_DECREMENT_PER_ATTACK * 1.5);
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
  public void receiveUseEntityPacket(ProtocolPacketEvent event, WrapperPlayClientInteractEntity packet) {
    Player player = event.getPlayer();
    User user = userOf(player);
    AttackRaytraceMeta meta = metaOf(user);
    AbilityMetadata abilities = user.meta().abilities();
    MovementMetadata movement = user.meta().movement();
    ViolationMetadata violationMeta = user.meta().violationLevel();

    WrapperPlayClientInteractEntity.InteractAction action = packet.getAction();

    // Only process attacks, interactions should not be checked
    if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
      List<Action> pendingActions = meta.queuedActions;
      int entityId = packet.getEntityId();
      Entity entity = EntityTracker.entityByIdentifier(user, entityId);
      // Allow attacks on invalid entity states
      if (entity == null
        || entity instanceof Entity.Destroyed
        || entity.isInVehicle() || !entity.isEntityAlive()
        || abilities.unsynchronizedHealth <= 0) {
        // check again?
        return;
      }

      if (user.meta().protocol().debugStates.containsKey("entity_pos_on_attack")) {
        String clientEntityPos = user.meta().protocol().debugStates.remove("entity_pos_on_attack");
        ByteBuf medium = FriendlyByteBuf.from256Unpooled();
        byte[] decodedInformation = Base64.getDecoder().decode(clientEntityPos);
        medium.writeBytes(decompress(decodedInformation));
        Position clientEntityPosition = Position.STREAM_CODEC.decode(medium);
        int strings = medium.readInt();
        List<String> stringsList = new ArrayList<>();

        for (int i = 0; i < strings; i++) {
          stringsList.add(FriendlyByteBuf.readUtf(medium, Short.MAX_VALUE));
        }
        Position serverEntityPosition = entity.position.toPosition();
        double distance = clientEntityPosition.distance(serverEntityPosition);
//        player.sendMessage(ChatColor.GOLD + "Server " + serverEntityPosition.format(12));
//        player.sendMessage(ChatColor.GREEN + "Client " + clientEntityPosition.format(12));
        player.sendMessage(ChatColor.YELLOW + " Server");
        int i = 0;
        for (String positionChange : entity.positionChanges()) {
          if (i++ >= 5) {
            player.sendMessage(ChatColor.YELLOW + positionChange);
          }
        }
        i = 0;
        player.sendMessage(ChatColor.DARK_PURPLE + " Client");
        for (String positionChange : stringsList) {
          if (i++ >= 5) {
            player.sendMessage(ChatColor.DARK_PURPLE + positionChange);
          }
        }
        player.sendMessage(ChatColor.RED + "Distance " + formatDouble(distance, 12));
      }

//      player.sendMessage(String.valueOf(entity.position.toPosition()));
      boolean inTeleport = movement.lastTeleport == 0 || violationMeta.isInActiveTeleportBundle;
      boolean firstRaytraceSuccessful = false;
      if (!inTeleport && !entityInTimeout(user, entity, entity.pendingFeedbackPackets())) {
        // Make a first attempt at ray-tracing to enhance player experience
        Raytrace raytrace = fireRaytraceFor(user, entity, computeExpansionFor(user, true), true);
        double blockReachDistance = Raytracing.reachDistanceOf(user);
        if (raytrace.reach() <= blockReachDistance) {
          firstRaytraceSuccessful = true;
        }
      }
      boolean pendingPushable = pendingActions.size() < MAX_ALLOWED_PENDING_ATTACKS;
      boolean resendLater = !firstRaytraceSuccessful || !pendingPushable;
      if (resendLater) {
        // Cancel attack and redirect it
        event.setCancelled(true);
      }
      if (user.receives(MessageChannel.DEBUG_PACKET_HOLD)) {
        if (resendLater) {
          Synchronizer.synchronize(() -> {
            player.sendMessage("%PH " + ChatColor.RED + "Await ATTACK at " + (System.currentTimeMillis() % 1000) + " since prelim ray failed");
          });
        } else {
          Synchronizer.synchronize(() -> {
            player.sendMessage("%PH " + ChatColor.GREEN + "Allowing ATTACK without hold at " + (System.currentTimeMillis() % 1000));
          });
        }
      }
      // Only add attack to queue if queue size is small enough
      if (pendingPushable) {
        Attack attack = new Attack(copyAttackPacket(packet), entityId, resendLater, entity.pendingFeedbackPackets());
        pendingActions.add(attack);
      } else {
        Violation violation = Violation.builderFor(AttackRaytrace.class)
          .forPlayer(player).withMessage("attacked too many entities at once")
          .withDetails("queued " + pendingActions.size() + " attacks")
          .withVL(0)
          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
          .build();
        Modules.violationProcessor().processViolation(violation);
      }
    }
  }


  private static byte[] decompress(byte[] input) {
    Inflater inflater = new Inflater();
    inflater.setInput(input);
    ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length);
    byte[] buf = new byte[1024];
    while (!inflater.finished()) {
      try {
        int count = inflater.inflate(buf);
        bos.write(buf, 0, count);
      } catch (DataFormatException e) {
        return new byte[0];
      }
    }
    try {
      bos.close();
    } catch (IOException ignored) {}
    return bos.toByteArray();
  }

  @PacketSubscription(
    priority = LOW,
    packetsIn = ARM_ANIMATION
  )
  public void receiveArmAnimationPacket(ProtocolPacketEvent event, WrapperPlayClientAnimation packet) {
    Player player = event.getPlayer();
    User user = userOf(player);
    AttackRaytraceMeta meta = metaOf(user);
    List<Action> pendingActions = meta.queuedActions;

    Action lastAction = pendingActions.isEmpty() ? null : pendingActions.get(pendingActions.size() - 1);
    if (!(lastAction instanceof Attack) || !((Attack) lastAction).shouldResend()) {
      return;
    }

    // Only add arm animations to queue if queue size is small enough
    if (pendingActions.size() < MAX_ALLOWED_PENDING_ATTACKS) {
      pendingActions.add(new ArmAnimation(copyAnimationPacket(packet)));
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    priority = NORMAL,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK}
  )
  public void receiveMovementPacket(ProtocolPacketEvent event, WrapperPlayClientPlayerFlying packet) {
    Player player = event.getPlayer();
    User user = userOf(player);
    AttackRaytraceMeta meta = metaOf(user);
    AbilityMetadata abilities = user.meta().abilities();
    MovementMetadata movement = user.meta().movement();
    ProtocolMetadata protocol = user.meta().protocol();
    List<Action> pendingAttacks = meta.queuedActions;
    // Clear attacks if recently teleported
    if (movement.lastTeleport <= 1 || movement.awaitTeleport) {
      pendingAttacks.clear();
    }
    // Apply flying packets (first boolean)
    if (!packet.hasPositionChanged()) {
      meta.flyingPacketCounter++;
    } else {
      meta.flyingPacketCounter = 0;
    }
    // Process all pending attacks
    for (Action pendingAction : pendingAttacks) {
      if (pendingAction instanceof Attack) {
        Attack pendingAttack = (Attack) pendingAction;
        float entityHealth = abilities.unsynchronizedHealth;
        Entity attackedEntity = EntityTracker.entityByIdentifier(user, pendingAttack.entityId);

        if (user.receives(MessageChannel.DEBUG_PACKET_HOLD)) {
          if (pendingAttack.shouldResend) {
            Synchronizer.synchronize(() -> {
              player.sendMessage("%PH " + ChatColor.YELLOW + "Processing ATTACK at " + (System.currentTimeMillis() % 1000));
            });
          }
        }

        // Once again ignore invalid entity states to make sure nothing is processed wrongly
        if (entityHealth <= 0
          || attackedEntity == null
          || attackedEntity instanceof Entity.Destroyed
          || (attackedEntity.hasTypeData() && attackedEntity.typeData().fireball())
        ) {
          redirectValidPacket(player, pendingAction.packet());
          continue;
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
            processAttackRaytraceFor(user, attackedEntity, pendingAttack, computeExpansionFor(user, false));
          }
        }
      } else if (pendingAction instanceof ArmAnimation) {
        redirectValidPacket(player, pendingAction.packet());
      }
    }
    pendingAttacks.clear();
  }

  // Block any latency abusing cheats
  private boolean entityInTimeout(User user, Entity attackedEntity, long pendingFeedbacks) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    ViolationMetadata violationLevel = meta.violationLevel();
    ConnectionMetadata connection = meta.connection();

    if (!attackedEntity.typeData().isLivingEntity()) {
      return false;
    }

    boolean isPlayer = attackedEntity.isPlayer;

    int maximumPendingFeedbackPackets = trustFactorSetting("pending-allowance", player) + (int) MathHelper.minmax(1, LatencyStudy.cachedAverage(), 20) + 2;
    LatencyStudy.enterHit((short) pendingFeedbacks);

    // protection 1: absolute limit
    boolean entityHasTimedOut = false;

    if (pendingFeedbacks >= maximumPendingFeedbackPackets + 2 && isPlayer) {
      Violation violation = Violation.builderFor(AttackRaytrace.class)
        .forPlayer(player).withCustomThreshold("timeout")
        .withVL(0.5)
        .withMessage("attacked player position too old")
        .withDetails("already " + pendingFeedbacks + " new packets, latency: " +user.latency() + "ms")
        .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
        .build();
      Modules.violationProcessor().processViolation(violation);
      entityHasTimedOut = true;
    }

    // protection 2: entity too far away
    double trustfactorBaseDistanceLimit = trustFactorSetting("pending-distance", player);
    double actualDistance = attackedEntity.serverClientPositionOffset();
    boolean distanceOverLimit = actualDistance > trustfactorBaseDistanceLimit;

//    if (distanceOverLimit && isPlayer) {
//      boolean needsToBeBlocked = user.trustFactor().atOrBelow(TrustFactor.RED);
//
//      Violation violation = Violation.builderFor(AttackRaytrace.class)
//        .forPlayer(player).withCustomThreshold("timeout")
//        .withVL(0.5)
//        .withMessage("attacked high-risk player position")
//        .withDetails("client/server offset is " + formatDouble(actualDistance, 2) + " blocks")
//        .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
//        .build();
//      Modules.violationProcessor().processViolation(violation);
//      if (needsToBeBlocked) {
//        entityHasTimedOut = true;
//      }
//    }

    // protection 3: short transaction ping based limit
    double multiplier = 0.95;// - ((violations.backtrackVL / 30) * 0.1);
    double shortTransactionPingAverage = connection.shortTransactionPingAverage() / 50d;
    double normalTransactionPingAverage = connection.transactionPingAverage() / 50d;
    double largerTransactionPingAverage = Math.max(shortTransactionPingAverage, normalTransactionPingAverage);

    double unroundedTicksOverLimit = pendingFeedbacks - Math.ceil((largerTransactionPingAverage * multiplier)/* + 0.25*/);
    int ticksOverLimit = (int) Math.floor(unroundedTicksOverLimit);

    FeedbackAnalysis feedbackAnalysis = Modules.feedbackAnalysis();
    long discrepancyInMs = feedbackAnalysis.entityLatencyDiscrepancy(user);
    if (discrepancyInMs > 50 && ticksOverLimit >= 0) {
      double general = feedbackAnalysis.generalLatency(user);
      double combat = feedbackAnalysis.entityNearLatency(user);

      if (Math.abs(general - combat) > 75) {
        Violation violation = Violation.builderFor(AttackRaytrace.class)
          .forPlayer(player).withCustomThreshold("timeout")
          .withVL(2)
          .withMessage("has different combat/idle latency")
          .withDetails(((int)general) + "ms to " + ((int)combat) + "ms combat")
          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
          .build();
        ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
        double after = violationContext.violationLevelAfter();
        if (after > 30) {
          entityHasTimedOut = true;
          user.nerf(AttackNerfStrategy.CANCEL, "67");
        }
      }

    }

    // protection 4: long-term protection
    List<LatencyInfo> suspiciousLatencies = feedbackAnalysis.suspiciousLatencies(user);
    if (!suspiciousLatencies.isEmpty() /*&& ticksOverLimit >= 0*/) {
      Comparator<LatencyInfo> comp = Comparator.comparing(LatencyInfo::latency);
      // sort, highest first
      suspiciousLatencies.sort(comp.reversed());
      suspiciousLatencies.removeIf(latencyInfo -> System.currentTimeMillis() - latencyInfo.issued() > 3000);

      if (!suspiciousLatencies.isEmpty()) {
        String entityName = attackedEntity.entityName();
        LatencyInfo mostSuspiciousLatency = suspiciousLatencies.get(0);
        boolean faring = mostSuspiciousLatency.faring();
        long highest = mostSuspiciousLatency.latency();
        double mean = feedbackAnalysis.meanLatency(user);
        double stdDev = feedbackAnalysis.stdDev(user);
        double zScore = (highest - mean) / stdDev;
        boolean frequencyMismatch = feedbackAnalysis.recentlyDetectedForFreqMisrep(user);
        double addedVl = (zScore > 4.5 ? 1 : 0.5);
        if (frequencyMismatch) {
          addedVl += 0.5;
        }
        if (faring) {
          addedVl += 0.25;
        }
        violationLevel.backtrackVL = Math.min(violationLevel.backtrackVL + addedVl, 13);
        if (violationLevel.backtrackVL > 3) {
          String extras = ((faring || frequencyMismatch) ? ", " : "") + (faring ? "f" : "") + (frequencyMismatch ? "m-"+feedbackAnalysis.lastFreqMisrepMessage(user).replace(" ", "") : "");
          // Message: attacked expired position of <entity>
          // Details: Latency ~ N(μ, σ²) shows attack outlier probability of <probability>%
          Violation violation = Violation.builderFor(AttackRaytrace.class)
            .forPlayer(player).withCustomThreshold("timeout")
            .withVL((violationLevel.backtrackVL - 3) * 0.5)
            .withMessage("delayed " + entityName.toLowerCase(Locale.ROOT) + " movement packets")
//            .withDetails("N("+((int)highest)+" | " + ((int)mean) + ", " + ((int)stdDev) + ") = " + formatDouble(latencyProbability * 100, 9) + "%")
//            .withDetails(((int) highest) + "ms unlikely: " + formatDouble(latencyProbability * 100, 9) + "%")
            .addGranular("EXPR", "N("+((int)highest)+" | " + ((int)mean) + ", " + ((int)stdDev) + ")")
            .addGranular("PROB", formatDouble(feedbackAnalysis.latencyProbability(user, highest) * 100, 12))
            .addGranular("FREQ", feedbackAnalysis.recentlyDetectedForFreqMisrep(user) ? feedbackAnalysis.lastFreqMisrepMessage(user) : "NONE")
            .withDetails(((int) highest) + ", "+((int) mean) + ", " + ((int) stdDev) + " = " + formatDouble(zScore, 2) + extras)
            .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
            .build();

          boolean certain = faring && frequencyMismatch;

          ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
          double after = violationContext.violationLevelAfter();

          if (certain && after > 5) {
            entityHasTimedOut = true;
            violationLevel.lastBacktrackHitCancelRequest = System.currentTimeMillis();
            user.nerf(AttackNerfStrategy.CANCEL, "BCKTRK");
          }
          if (after > 30 /*&& !IntaveControl.GOMME_MODE*/) {
            entityHasTimedOut = true;
            violationLevel.lastBacktrackHitCancelRequest = System.currentTimeMillis();
            user.nerf(AttackNerfStrategy.CANCEL, "67");
          }

          violationLevel.lastBacktrackVLChange = System.currentTimeMillis();
        }
      }
      suspiciousLatencies.clear();
    }

    if (System.currentTimeMillis() - violationLevel.lastBacktrackHitCancelRequest < 5_000 && ticksOverLimit >= -1) {
      entityHasTimedOut = true;
    }

    if (!entityHasTimedOut) {
      timeoutDecrementer.decrement(user, 0.01);
    }

    if (System.currentTimeMillis() - violationLevel.lastBacktrackVLChange > 7_500) {
      violationLevel.backtrackVL = Math.max(0, violationLevel.backtrackVL - 1);
      violationLevel.lastBacktrackVLChange = System.currentTimeMillis();
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
    Raytrace lowestRaytrace = fireRaytraceFor(user, entity, 0.25f, false);
    double blockReachDistance = Raytracing.reachDistanceOf(meta);
    // Iteratively find out reach if ray-trace wasn't valid
    if (lowestRaytrace.reach() > blockReachDistance) {
      double reach = findLowestPossibleReachIterative(user, entity);
      // We don't use the positions here anyway, just fill them with empty ones
//      Position emptyPosition = new Position(0, 0, 0);
      lowestRaytrace = new Raytrace(lowestRaytrace.from(), lowestRaytrace.to(), reach);
    }
    processResult(user, lowestRaytrace, entity, attack, 0.25f, true);
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
//    if (movement.receivedFlyingPacketIn(1) && user.protocolVersion() >= VER_1_9) {
      double reach = findLowestPossibleReachIterative(user, entity, true);
      minReach = Math.min(minReach, reach);
//    }
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
    double minReach = 10;
    HistoryWindow<Entity.EntityPositionContext> history = entity.positionHistory;
    int maximumPendingFeedbackPackets =
      trustFactorSetting("pending-allowance", player)
        + (int) MathHelper.minmax(0, LatencyStudy.cachedAverage(), 20);
    double blockReachDistance = Raytracing.reachDistanceOf(meta);
    boolean livingEntity = entity.typeData().isLivingEntity();

    int availableHistory = history.size();
    for (int ticksAgo = 0; ticksAgo < availableHistory; ticksAgo++) {
      if (ticksAgo > maximumPendingFeedbackPackets) {
        continue;
      }
      Entity.EntityPositionContext possiblePosition = history.back(ticksAgo);
      entity.position = possiblePosition.clone();
      Raytrace resultWithoutIncrement = fireRaytraceFor(user, entity, 0.13f, currentPosition);
      if (resultWithoutIncrement.reach() < blockReachDistance) {
        return resultWithoutIncrement.reach();
      }
      double minReachInItr = resultWithoutIncrement.reach();
      int limit = 5;
      while (entity.position.newPosRotationIncrements > 0 && livingEntity) {
        if (limit-- <= 0) {
          break;
        }
        entity.onUpdate();
        Raytrace result = fireRaytraceFor(user, entity, 0.13f, currentPosition);
        if (result.reach() < blockReachDistance) {
          return result.reach();
        }
        minReachInItr = Math.min(minReachInItr, result.reach());
      }
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
   * @param"attacked  The attacked entity
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

    Map<String, String> granular = new LinkedHashMap<>();
    granular.put("name", attacked.entityName());
    granular.put("size", String.valueOf(attacked.typeData().size()));

    List<String> positionChanges = attacked.positionChanges();
    if (positionChanges != null && !positionChanges.isEmpty()) {
      int size = positionChanges.size();
      for (int i = 5; i < size; i++) {
        granular.put(String.valueOf((i - size)), positionChanges.get(i));
      }
    }
    switch (result) {
      case MISS: {
        message = String.format(
          "attacked %s %s out of sight %s",
          resolveArticle(entityName), entityName.toLowerCase(), estimationSuffix
        );
        details = "missed hit";
//        if (IntaveControl.GOMME_MODE) {
//        } else {
//          details = raytrace.from() + " ray to " + (raytrace.to() == null ? "/" + attacked.position.toPosition() + "/" : raytrace.to());
//        }
        granular.put("TYPE", "MISS");
        granular.put("RAY_FROM", raytrace.from().toString());
        granular.put("RAY_TO", raytrace.to() == null ? "null" : raytrace.to().toString());
        thresholdKey = "applicable-thresholds.hitbox";
        sibyl = String.format(
          "%s/%d missed hit on %s",
          player.getName(), user.protocolVersion(), entityName.toLowerCase()
        );
        metaOf(user).lastReachDetection = System.currentTimeMillis();
        reach = 10;
        break;
      }
      case REACH: {
        String displayReach = formatDouble(raytrace.reach(), 4);
        message = String.format(
          "attacked %s %s from too far away %s",
          resolveArticle(entityName), entityName.toLowerCase(), estimationSuffix
        );
//        if (IntaveControl.GOMME_MODE) {
          details = displayReach + " blocks";
//        } else {
//          details = raytrace.from() + " ray to " + (raytrace.to() == null ? "/" + attacked.position.toPosition() + "/" : raytrace.to()) + " :: " + displayReach + " blocks";
//        }
        granular.put("TYPE", "REACH");
        granular.put("RAY_FROM", raytrace.from().toString());
        granular.put("RAY_TO", raytrace.to() == null ? "null" : raytrace.to().toString());
        granular.put("REACH", displayReach);
        thresholdKey = "applicable-thresholds.reach";
        sibyl = String.format(
          "%s/%d attacked %s from %s",
          player.getName(), user.protocolVersion(), entityName.toLowerCase(), displayReach
        );
        reach = raytrace.reach();
        metaOf(user).lastReachDetection = System.currentTimeMillis();
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

    granular.put("s/c v", MinecraftVersion.getCurrentVersion().getVersion() + " / " + user.protocolVersion());
    DebugBroadcast.broadcast(player, MessageCategory.ATRAFLT, MessageSeverity.HIGH, sibyl, sibyl);
    Violation violation = Violation.builderFor(AttackRaytrace.class)
      .forPlayer(player).withMessage(message).withDetails(details)
      .withCustomThreshold(thresholdKey).withVL(vl)
      .withGranulars(granular)
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
    // Only allow attack if player has bypass trust-factor
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
  private void redirectValidPacket(Player player, PacketWrapper<?> packet) {
    PacketEvents.getAPI().getPlayerManager().receivePacketSilently(player, packet);
  }

  private WrapperPlayClientInteractEntity copyAttackPacket(WrapperPlayClientInteractEntity packet) {
    return new WrapperPlayClientInteractEntity(
      packet.getEntityId(),
      packet.getAction(),
      packet.getLocation(),
      packet.getHand(),
      packet.isSneaking().orElse(false)
    );
  }

  private WrapperPlayClientAnimation copyAnimationPacket(WrapperPlayClientAnimation packet) {
    return new WrapperPlayClientAnimation(packet.getHand());
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
    Position targetPosition = raytrace.to();
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
      vl /= 2;
    }
    attackRaytraceMeta.lastPosition = raytrace.to();
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
  private float computeExpansionFor(User user, boolean isPre) {
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();
    MovementMetadata movement = meta.movement();
    AttackRaytraceMeta attackRaytraceMeta = metaOf(user);

    float baseline = 0;
    // Process 1.8 and lower
    if (clientData.flyingPacketsAreSent()) {
      baseline = attackRaytraceMeta.flyingPacketCounter > 0 ? 0.13f : 0.1f;
    } else {
      baseline = 0.13f;
    }
    // Process 1.9 and higher
    if (isPre && System.currentTimeMillis() - attackRaytraceMeta.lastReachDetection > 20_000) {
      baseline += 0.75f;
    }
    return baseline;
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
    public List<Action> queuedActions = new ArrayList<>();
    public Position lastPosition;
    public long lastReachDetection = 0;
  }

  /**
   * The attack stored to be processed after a movement packet was sent by the client
   *
   * @since 14.5.8
   */
  public static class Attack implements Action {
    private final boolean shouldResend;
    private final WrapperPlayClientInteractEntity packet;
    private final int entityId;
    private final long pendingFeedbackPackets;
    private final long timestamp = System.currentTimeMillis();

    public Attack(WrapperPlayClientInteractEntity packet, int entityId, boolean shouldResend, long pendingFeedbackPackets) {
      this.packet = packet;
      this.entityId = entityId;
      this.shouldResend = shouldResend;
      this.pendingFeedbackPackets = pendingFeedbackPackets;
    }

    public PacketWrapper<?> packet() {
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

    public long delay() {
      return System.currentTimeMillis() - timestamp;
    }
  }

  public static class ArmAnimation implements Action {
    private final WrapperPlayClientAnimation packet;

    public ArmAnimation(WrapperPlayClientAnimation packet) {
      this.packet = packet;
    }

    public PacketWrapper<?> packet() {
      return packet;
    }
  }

  public interface Action {
    PacketWrapper<?> packet();
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
