/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.report;

import com.google.gson.*;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.MoveMetric;
import de.jpx3.intave.check.movement.physics.environment.PostTickSimulation;
import de.jpx3.intave.check.movement.physics.evaluation.MaskedMotionTolerance;
import de.jpx3.intave.codec.JsonStreamCodecs;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.WorldHeight;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.share.ClientMath.floor;

/**
 * A point-in-time diagnostic snapshot of state that can influence movement simulation and
 * evaluation. The surrounding block region is captured at construction so later cache
 * invalidation cannot change the environment described by the report.
 */
public final class PhysicsReport implements Report {
  private static final int MAX_SURROUNDING_BLOCKS = 512;

  private final User suspect;
  private final long createdAt;
  private final BlockStateRegion surroundingBlocks;

  public PhysicsReport(User suspect) {
    this.suspect = suspect;
    this.createdAt = System.currentTimeMillis();
    this.surroundingBlocks = captureSurroundingBlocks(suspect);
  }

  @Override
  public User suspect() {
    return suspect;
  }

  @Override
  public JsonObject toJson() {
    MetadataBundle metadata = suspect.meta();
    MovementMetadata movement = metadata.movement();

    JsonObject report = new JsonObject();
    report.addProperty("type", "physics");
    report.addProperty("version", "1.0.0");
    report.addProperty("intaveVersion", IntavePlugin.fullVersion());
    report.addProperty("createdAt", createdAt);
    report.addProperty("userId", suspect.id().toString());
    report.add("movement", movementToJson(movement, metadata.violationLevel(), surroundingBlocks));
    report.add("protocol", protocolToJson(metadata.protocol()));
    report.add("abilities", abilitiesToJson(metadata.abilities(), suspect.hasPlayer()));
    report.add("effects", effectsToJson(metadata.potions()));
    report.add("inventory", inventoryToJson(metadata.inventory()));
    return report;
  }

  private static JsonObject movementToJson(
    MovementMetadata movement,
    ViolationMetadata physicsState,
    BlockStateRegion surroundingBlocks
  ) {
    JsonObject json = new JsonObject();

    JsonObject position = new JsonObject();
    position.add("current", positionToJson(movement.position()));
    position.add("last", positionToJson(movement.lastPosition()));
    position.add("verifiedLast", positionToJson(movement.verifiedLastPosition()));
    json.add("positions", position);

    JsonObject rotation = new JsonObject();
    rotation.add("current", rotationToJson(movement.rotation()));
    rotation.add("last", rotationToJson(movement.lastRotation()));
    rotation.add("lookVector", vectorToJson(movement.lookVector()));
    addNumber(rotation, "yawSine", movement.yawSine());
    addNumber(rotation, "yawCosine", movement.yawCosine());
    json.add("rotations", rotation);

    JsonObject input = new JsonObject();
    input.add("current", inputToJson(movement.input));
    input.add("last", inputToJson(movement.lastInput));
    input.addProperty("lastForward", movement.lastKeyForward);
    input.addProperty("lastStrafe", movement.lastKeyStrafe);
    json.add("input", input);

    SimulationResult simulationResult = movement.simulationResult();
    boolean validSimulation = simulationResult != null && simulationResult.isValid();
    JsonObject motion = new JsonObject();
    motion.add("sentOffset", motionToJson(movement.sentOffsetMotion()));
    motion.add(
      "simulatedOffset",
      nullableMotionToJson(validSimulation ? simulationResult.offsetMotion() : null)
    );
    motion.add(
      "actual",
      nullableMotionToJson(validSimulation ? simulationResult.actualMotion() : null)
    );
    motion.add("base", motionToJson(movement.mutableBaseMotionCopy()));
    motion.add("multiplier", nullableVectorToJson(movement.motionMultiplier()));
    addNumber(motion, "resetThreshold", movement.resetMotion());
    json.add("motions", motion);

    json.add("boundingBox", boundingBoxToJson(movement.boundingBox()));
    json.add(
      "surroundingBlocks",
      JsonStreamCodecs.encodeToTree(BlockStateRegion.JSON_CODEC, surroundingBlocks)
    );
    List<Motion> postTickMotions = new ArrayList<>();
    JsonArray postTickPriorSprinting = new JsonArray();
    for (PostTickSimulation candidate : movement.postTickMotionCandidates()) {
      postTickMotions.add(candidate.motion());
      postTickPriorSprinting.add(new JsonPrimitive(candidate.priorSprinting()));
    }
    json.add(
      "postTickMotionCandidates",
      JsonStreamCodecs.encodeToTree(
        Motion.LIST_JSON_CODEC,
        postTickMotions
      )
    );
    json.add("postTickMotionCandidatePriorSprinting", postTickPriorSprinting);
    json.add("simulationResult", simulationResultToJson(simulationResult));
    json.add("lastMovementConfiguration", configurationToJson(movement.lastMovementConfiguration()));
    json.add("moveMetrics", metricsToJson(movement));

    JsonObject state = new JsonObject();
    state.addProperty("pose", movement.pose().name());
    state.addProperty("simulator", movement.simulator().getClass().getSimpleName());
    state.addProperty("onLadderLast", movement.onLadderLast);
    state.addProperty("collidedWithBoat", movement.collidedWithBoat());
    state.addProperty("lastOnGround", movement.lastOnGround());
    state.addProperty("inWater", movement.inWater());
    state.addProperty("inLava", movement.inLava());
    state.addProperty("inWeb", movement.inWeb());
    state.addProperty("inVehicle", movement.isInVehicle());
    state.addProperty("sneaking", movement.isSneaking());
    state.addProperty("lastSneaking", movement.lastSneaking());
    state.addProperty("sprinting", movement.isSprinting());
    state.addProperty("lastSprinting", movement.lastSprinting());
    state.addProperty("sleeping", movement.isSleeping());
    state.addProperty("hasSprintSpeed", movement.hasSprintSpeed());
    state.addProperty("sprintingAllowed", movement.sprintingAllowed());
    state.addProperty("currentlyInBlock", movement.currentlyInBlock());
    state.addProperty("pushedByEntity", movement.pushedByEntity());
    state.addProperty("unpredictableVelocityExpected", movement.physicsUnpredictableVelocityExpected());
    state.addProperty("enforceBoatStep", movement.enforceBoatStep());
    state.addProperty("eyesInWater", movement.areEyesInWater());
    json.add("state", state);

    JsonObject characteristics = new JsonObject();
    addNumber(characteristics, "baseMoveSpeed", movement.baseMoveSpeed());
    addNumber(characteristics, "jumpMotion", movement.jumpMotion());
    addNumber(characteristics, "gravity", movement.gravity());
    addNumber(characteristics, "eyeHeight", movement.eyeHeight());
    addNumber(characteristics, "width", movement.width());
    addNumber(characteristics, "height", movement.height());
    addNumber(characteristics, "widthRounded", movement.widthRounded());
    addNumber(characteristics, "heightRounded", movement.heightRounded());
    addNumber(characteristics, "stepHeight", movement.stepHeight());
    addNumber(characteristics, "fallDistance", movement.fallDistance());
    addNumber(characteristics, "frictionMultiplier", movement.frictionMultiplier());
    addNumber(characteristics, "frictionPositionSubtraction", movement.frictionPosSubtraction());
    addNumber(characteristics, "jumpMovementFactor", movement.rawJumpMovementFactor());
    addNumber(characteristics, "aiMoveSpeed", movement.aiMoveSpeed(movement.isSprinting()));
    characteristics.addProperty("soulSpeedAffected", movement.blockOnPositionSoulSpeedAffected());
    characteristics.addProperty("collideMaterial", movement.collideMaterial().name());
    characteristics.addProperty("previousCollideMaterial", movement.previousCollideMaterial().name());
    characteristics.addProperty("frictionMaterial", movement.frictionMaterial().name());
    characteristics.addProperty("previousFrictionMaterial", movement.previousFrictionMaterial().name());
    characteristics.addProperty("fireworkRocketsPower", movement.fireworkRocketsPower());
    characteristics.addProperty("activeFireworkRockets", movement.activeFireworkRockets());
    characteristics.addProperty("highestLocalRiptideLevel", movement.highestLocalRiptideLevel());
    characteristics.addProperty("onGroundWithRiptide", movement.onGroundWithRiptide());
    json.add("characteristics", characteristics);

    JsonObject tolerances = new JsonObject();
    tolerances.add("maskedMotion", maskedMotionToleranceToJson(movement.maskedMotionTolerance));
    JsonObject shulker = new JsonObject();
    shulker.addProperty("remainingX", movement.shulkerXToleranceRemaining());
    shulker.addProperty("remainingY", movement.shulkerYToleranceRemaining());
    shulker.addProperty("remainingZ", movement.shulkerZToleranceRemaining());
    shulker.addProperty("lowestY", movement.lowestShulkerY());
    shulker.addProperty("highestY", movement.highestShulkerY());
    tolerances.add("shulker", shulker);
    JsonObject piston = new JsonObject();
    piston.addProperty("remaining", movement.pistonMotionToleranceRemaining());
    addNumber(piston, "horizontalAllowance", movement.pistonHorizontalAllowance());
    addNumber(piston, "verticalAllowance", movement.pistonVerticalAllowance());
    piston.add("collisionArea", nullableBoundingBoxToJson(movement.pistonCollisionArea()));
    tolerances.add("piston", piston);
    json.add("tolerances", tolerances);

    JsonObject packetHistory = new JsonObject();
    packetHistory.addProperty("currentTick", movement.currentTick());
    packetHistory.addProperty("activeSequence", movement.activeSequence());
    packetHistory.addProperty("physicsPacketRelinkFlyVL", movement.physicsPacketRelinkFlyVL());

    JsonObject recentFlyingPackets = new JsonObject();
    for (int ticks = 0; ticks <= 10; ticks++) {
      recentFlyingPackets.addProperty(Integer.toString(ticks), movement.receivedFlyingPacketIn(ticks));
    }
    packetHistory.add("receivedFlyingPacketIn", recentFlyingPackets);
    json.add("packetHistory", packetHistory);

    JsonObject evaluationState = new JsonObject();
    addNumber(evaluationState, "physicsOffset", physicsState.physicsOffset);
    addNumber(evaluationState, "physicsViolationLevel", physicsState.physicsVL);
    addNumber(evaluationState, "insignificantBuffer", physicsState.physicsInsignificantBufferVL);
    addNumber(evaluationState, "velocityViolationLevel", physicsState.physicsVelocityVL);
    addNumber(evaluationState, "invalidMovementsInRow", physicsState.physicsInvalidMovementsInRow);
    json.add("evaluationState", evaluationState);
    return json;
  }

  private static BlockStateRegion captureSurroundingBlocks(User user) {
    MovementMetadata movement = user.meta().movement();
    BoundingBox verifiedBox = BoundingBox.fromPosition(
      user,
      movement,
      movement.verifiedLastPosition()
    );
    BoundingBox receivedBox = BoundingBox.fromPosition(user, movement, movement.position());
    BoundingBox simulatedSweep = simulationSweep(verifiedBox, movement.simulationResult());
    BoundingBox region = movement.boundingBox()
      .union(verifiedBox)
      .union(receivedBox)
      .union(simulatedSweep)
      .grow(1);
    int minX = floor(region.minX);
    int maxX = floor(region.maxX);
    int minY = clampWorldY(floor(region.minY) - 1);
    int maxY = clampWorldY(floor(region.maxY));
    int minZ = floor(region.minZ);
    int maxZ = floor(region.maxZ);

    BlockCache blockCache = user.blockCache();
    List<PositionedBlockState> nonAirBlocks = new ArrayList<>();
    int visited = 0;
    boolean complete = true;

    scan:
    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        for (int y = minY; y <= maxY; y++) {
          if (visited++ >= MAX_SURROUNDING_BLOCKS) {
            complete = false;
            break scan;
          }
          BlockState state = blockCache.peekStateAt(x, y, z);
          if (state == null) {
            complete = false;
            continue;
          }
          if (state.type() != Material.AIR) {
            nonAirBlocks.add(new PositionedBlockState(BlockPosition.of(x, y, z), state));
          }
        }
      }
    }

    return new BlockStateRegion(
      BlockPosition.of(minX, minY, minZ),
      BlockPosition.of(maxX, maxY, maxZ),
      complete,
      nonAirBlocks
    );
  }

  private static BoundingBox simulationSweep(
    BoundingBox startingBox,
    SimulationResult simulationResult
  ) {
    if (simulationResult == null) {
      return startingBox;
    }
    BoundingBox sweep = startingBox;
    Motion[] candidates = {
      simulationResult.actualMotion(),
      simulationResult.intermittentResult(),
      simulationResult.offsetMotion()
    };
    for (Motion candidate : candidates) {
      if (candidate != null) {
        sweep = sweep.union(startingBox.expand(candidate));
      }
    }
    return sweep;
  }

  private static int clampWorldY(int positionY) {
    return Math.max(
      WorldHeight.LOWER_WORLD_LIMIT,
      Math.min(positionY, WorldHeight.UPPER_WORLD_LIMIT)
    );
  }

  private static JsonObject protocolToJson(ProtocolMetadata protocol) {
    JsonObject json = new JsonObject();
    json.addProperty("protocolVersion", protocol.protocolVersion());
    json.addProperty("version", protocol.versionString());
    json.addProperty("legacyTeleportAccept", protocol.legacyTeleportAccept());
    json.addProperty("emptyFlyingPacketsAreExplicitlySent", protocol.emptyFlyingPacketsAreExplicitlySent());
    json.addProperty("applyModernCollider", protocol.applyModernCollider());
    json.addProperty("swimmingMechanics", protocol.swimmingMechanics());
    json.addProperty("canUseElytra", protocol.canUseElytra());
    json.addProperty("clientsideElytra", protocol.clientsideElytra());
    json.addProperty("serversideElytra", protocol.serversideElytra());
    json.addProperty("combatUpdate", protocol.combatUpdate());
    json.addProperty("roundEnvironmentNumbers", protocol.roundEnvironmentNumbers());
    json.addProperty("canSprintWhileSneaking", protocol.canSprintWhileSneaking());
    json.addProperty("sprintWhenHandActive", protocol.sprintWhenHandActive());
    json.addProperty("viaVersionShieldBlockReplacement", protocol.viaVersionShieldBlockReplacement());
    json.addProperty("delayedSneak", protocol.delayedSneak());
    json.addProperty("alternativeSneak", protocol.alternativeSneak());
    json.addProperty("motionResetOnCollision", protocol.motionResetOnCollision());
    json.addProperty("cavesAndCliffsUpdate", protocol.cavesAndCliffsUpdate());
    json.addProperty("useItemMovementPacket", protocol.useItemMovementPacket());
    json.addProperty("maskedMotionPossible", protocol.maskedMotionPossible());
    json.addProperty("beeUpdate", protocol.beeUpdate());
    json.addProperty("aquaticUpdate", protocol.aquaticUpdate());
    json.addProperty("trailsAndTailsUpdate", protocol.trailsAndTailsUpdate());
    json.addProperty("newMotionClampLogic", protocol.newMotionClampLogic());
    json.addProperty("newBlockEntityIntersectionLogic", protocol.newBlockEntityIntersectionLogic());
    json.addProperty("sendsClientTickEnd", protocol.sendsClientTickEnd());
    json.addProperty("sendsInputs", protocol.sendsInputs());
    addNumber(json, "flyingPacketUncertaintyRadius", protocol.flyingPacketUncertaintyRadius());
    return json;
  }

  private static JsonObject abilitiesToJson(AbilityMetadata abilities, boolean hasPlayer) {
    JsonObject json = new JsonObject();
    json.addProperty("probablyFlying", hasPlayer ? abilities.probablyFlying() : abilities.allowFlying());
    json.addProperty("allowFlying", abilities.allowFlying());
    addNumber(json, "flySpeed", abilities.flySpeed());
    json.addProperty("ignoringMovementPackets", abilities.ignoringMovementPackets());
    json.addProperty("foodLevel", abilities.foodLevel);
    addNumber(json, "movementSpeed", abilities.attributeValue("generic.movementSpeed"));
    addNumber(json, "sneakingSpeed", abilities.attributeValue("player.sneaking_speed"));
    addNumber(json, "jumpStrength", abilities.jumpStrength());
    addNumber(json, "scale", abilities.scale());
    return json;
  }

  private static JsonObject effectsToJson(EffectMetadata effects) {
    JsonObject json = new JsonObject();
    json.addProperty("speedAmplifier", effects.potionEffectSpeedAmplifier());
    json.addProperty("speedDuration", effects.potionEffectSpeedDuration);
    json.addProperty("slownessAmplifier", effects.potionEffectSlownessAmplifier());
    json.addProperty("slownessDuration", effects.potionEffectSlownessDuration);
    json.addProperty("jumpAmplifier", effects.potionEffectJumpAmplifier());
    json.addProperty("jumpDuration", effects.potionEffectJumpDuration);
    return json;
  }

  private static JsonObject inventoryToJson(InventoryMetadata inventory) {
    JsonObject json = new JsonObject();
    json.addProperty("handActive", inventory.handActive());
    json.addProperty("handActiveTicks", inventory.handActiveTicks);
    json.addProperty("pastHotBarSlotChange", inventory.pastHotBarSlotChange);
    json.addProperty("inventoryOpen", inventory.inventoryOpen());
    json.addProperty("heldItemType", inventory.heldItemType().name());
    json.addProperty("offhandItemType", inventory.offhandItemType().name());
    json.addProperty("activeItemType", inventory.activeItemType().name());
    json.addProperty("foodItem", inventory.foodItem());
    json.addProperty("usableItemAvailable", inventory.usableItemInEitherHandOrHotbar());
    json.addProperty("couldChargeCrossbow", inventory.couldChargeCrossbow());
    return json;
  }

  private static JsonObject metricsToJson(MovementMetadata movement) {
    JsonObject metrics = new JsonObject();
    for (MoveMetric metric : MoveMetric.values()) {
      JsonObject values = new JsonObject();
      values.addProperty("ticks", movement.ticks(metric));
      values.addProperty("ticksPast", movement.ticksPast(metric));
      metrics.add(metric.name(), values);
    }
    return metrics;
  }

  private static JsonObject simulationResultToJson(SimulationResult result) {
    return JsonStreamCodecs.encodeToTree(SimulationResult.JSON_CODEC, result).getAsJsonObject();
  }

  private static JsonObject configurationToJson(MovementConfiguration configuration) {
    if (configuration == null) {
      return new JsonObject();
    }
    return JsonStreamCodecs.encodeToTree(
      MovementConfiguration.JSON_CODEC,
      configuration
    ).getAsJsonObject();
  }

  private static JsonObject maskedMotionToleranceToJson(MaskedMotionTolerance tolerance) {
    return JsonStreamCodecs.encodeToTree(
      MaskedMotionTolerance.JSON_CODEC,
      tolerance
    ).getAsJsonObject();
  }

  private static JsonElement numberToJson(Number number) {
    if (number instanceof Double && !Double.isFinite(number.doubleValue())) {
      return new JsonPrimitive(number.toString());
    }
    if (number instanceof Float && !Float.isFinite(number.floatValue())) {
      return new JsonPrimitive(number.toString());
    }
    return new JsonPrimitive(number);
  }

  private static void addNumber(JsonObject json, String name, Number value) {
    json.add(name, numberToJson(value));
  }

  private static JsonObject motionToJson(Motion motion) {
    return JsonStreamCodecs.encodeToTree(Motion.JSON_CODEC, motion).getAsJsonObject();
  }

  private static JsonElement nullableMotionToJson(Motion motion) {
    return motion == null ? JsonNull.INSTANCE : motionToJson(motion);
  }

  private static JsonObject positionToJson(Position position) {
    return JsonStreamCodecs.encodeToTree(Position.JSON_CODEC, position).getAsJsonObject();
  }

  private static JsonObject rotationToJson(Rotation rotation) {
    return JsonStreamCodecs.encodeToTree(Rotation.JSON_CODEC, rotation).getAsJsonObject();
  }

  private static JsonObject inputToJson(Input input) {
    return JsonStreamCodecs.encodeToTree(Input.JSON_CODEC, input).getAsJsonObject();
  }

  private static JsonObject vectorToJson(Vector vector) {
    JsonObject json = new JsonObject();
    json.add("x", numberToJson(vector.getX()));
    json.add("y", numberToJson(vector.getY()));
    json.add("z", numberToJson(vector.getZ()));
    return json;
  }

  private static JsonElement nullableVectorToJson(Vector vector) {
    return vector == null ? JsonNull.INSTANCE : vectorToJson(vector);
  }

  private static JsonObject boundingBoxToJson(BoundingBox box) {
    return JsonStreamCodecs.encodeToTree(BoundingBox.JSON_CODEC, box).getAsJsonObject();
  }

  private static JsonElement nullableBoundingBoxToJson(BoundingBox box) {
    return box == null ? JsonNull.INSTANCE : boundingBoxToJson(box);
  }

}
