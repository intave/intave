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

package de.jpx3.intave.share;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.evaluation.MaskedMotionTolerance;
import de.jpx3.intave.codec.JsonStreamCodecs;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static de.jpx3.intave.share.Direction.Axis.*;
import static org.junit.jupiter.api.Assertions.*;

final class JsonCodecTest {
  @Test
  void motionJsonCodecRoundTripsAndSkipsUnknownFields() {
    Motion expected = new Motion(1.25, -2.5, 3.75);

    String encoded = JsonStreamCodecs.encodeToString(Motion.JSON_CODEC, expected);

    assertEquals("{\"x\":1.25,\"y\":-2.5,\"z\":3.75}", encoded);
    assertEquals(expected, JsonStreamCodecs.decodeFromString(Motion.JSON_CODEC, encoded));
    assertEquals(
      expected,
      JsonStreamCodecs.decodeFromString(
        Motion.JSON_CODEC,
        "{\"unknown\":{\"nested\":true},\"z\":3.75,\"x\":1.25,\"y\":-2.5}"
      )
    );
  }

  @Test
  void positionJsonCodecRoundTripsAndSkipsUnknownFields() {
    Position expected = new Position(-10.5, 64.25, 1024.75);

    String encoded = JsonStreamCodecs.encodeToString(Position.JSON_CODEC, expected);

    assertEquals("{\"x\":-10.5,\"y\":64.25,\"z\":1024.75}", encoded);
    assertEquals(expected, JsonStreamCodecs.decodeFromString(Position.JSON_CODEC, encoded));
    assertEquals(
      expected,
      JsonStreamCodecs.decodeFromString(
        Position.JSON_CODEC,
        "{\"z\":1024.75,\"ignored\":[1,2,3],\"y\":64.25,\"x\":-10.5}"
      )
    );
  }

  @Test
  void rotationJsonCodecRoundTripsAndSkipsUnknownFields() {
    Rotation expected = new Rotation(135.5F, -42.25F);

    String encoded = JsonStreamCodecs.encodeToString(Rotation.JSON_CODEC, expected);

    assertEquals("{\"yaw\":135.5,\"pitch\":-42.25}", encoded);
    assertEquals(expected, JsonStreamCodecs.decodeFromString(Rotation.JSON_CODEC, encoded));
    assertEquals(
      expected,
      JsonStreamCodecs.decodeFromString(
        Rotation.JSON_CODEC,
        "{\"ignored\":null,\"pitch\":-42.25,\"yaw\":135.5}"
      )
    );
  }

  @Test
  void inputJsonCodecRoundTripsAndSkipsUnknownFields() {
    Input expected = new Input(true, false, true, false, true, false, true);

    String encoded = JsonStreamCodecs.encodeToString(Input.JSON_CODEC, expected);

    assertEquals(
      "{\"forward\":true,\"backward\":false,\"left\":true,\"right\":false," +
        "\"jump\":true,\"sneaking\":false,\"sprinting\":true}",
      encoded
    );
    assertEquals(expected, JsonStreamCodecs.decodeFromString(Input.JSON_CODEC, encoded));
    assertEquals(
      expected,
      JsonStreamCodecs.decodeFromString(
        Input.JSON_CODEC,
        "{\"sprinting\":true,\"unknown\":{},\"sneaking\":false,\"jump\":true," +
          "\"right\":false,\"left\":true,\"backward\":false,\"forward\":true}"
      )
    );
  }

  @Test
  void boundingBoxJsonCodecRoundTripsOriginState() {
    BoundingBox expected = BoundingBox.originFrom(-1.5, 2.25, 3.5, 4.75, 5.0, 6.125);

    BoundingBox decoded = JsonStreamCodecs.decodeFromString(
      BoundingBox.JSON_CODEC,
      JsonStreamCodecs.encodeToString(BoundingBox.JSON_CODEC, expected)
    );

    assertEquals(expected, decoded);
    assertTrue(decoded.isOriginBox());
  }

  @Test
  void blockShapeJsonCodecRoundTripsCanonicalGeometry() {
    String empty = JsonStreamCodecs.encodeToString(
      BlockShape.JSON_CODEC,
      BlockShapes.emptyShape()
    );
    assertEquals("[]", empty);
    assertTrue(JsonStreamCodecs.decodeFromString(BlockShape.JSON_CODEC, empty).isEmpty());

    BlockShape expected = BlockShapes.optimizedMerge(
      BoundingBox.fromBounds(1.0, 2.0, 3.0, 1.5, 3.0, 4.0),
      BoundingBox.fromBounds(1.5, 2.25, 3.5, 2.0, 3.0, 4.0)
    );

    String encoded = JsonStreamCodecs.encodeToString(BlockShape.JSON_CODEC, expected);
    BlockShape decoded = JsonStreamCodecs.decodeFromString(BlockShape.JSON_CODEC, encoded);

    assertTrue(encoded.startsWith("["));
    assertEquals(expected.elementaryBoxes(), decoded.elementaryBoxes());
    assertEquals(expected.max(X_AXIS), decoded.max(X_AXIS));
    assertEquals(expected.max(Y_AXIS), decoded.max(Y_AXIS));
    assertEquals(expected.max(Z_AXIS), decoded.max(Z_AXIS));

    BlockShape mixedContexts = BlockShapes.merge(Arrays.asList(
      BoundingBox.originFrom(0.0, 0.0, 0.0, 0.25, 1.0, 1.0),
      BoundingBox.fromBounds(8.25, 64.0, 8.0, 8.5, 65.0, 9.0),
      BoundingBox.fromBounds(8.5, 64.0, 8.0, 9.0, 65.0, 9.0)
    ));
    BlockShape decodedMixedContexts = JsonStreamCodecs.decodeFromString(
      BlockShape.JSON_CODEC,
      JsonStreamCodecs.encodeToString(BlockShape.JSON_CODEC, mixedContexts)
    );
    assertEquals(mixedContexts.elementaryBoxes(), decodedMixedContexts.elementaryBoxes());
    assertTrue(decodedMixedContexts.elementaryBoxes().get(0).isOriginBox());
    assertFalse(decodedMixedContexts.elementaryBoxes().get(1).isOriginBox());
  }

  @Test
  void blockStateJsonCodecUsesCollisionOnlyProjection() {
    BlockShape outline = BlockShapes.cubeAt(4, 5, 6);
    BlockShape collision = BoundingBox.fromBounds(4.0, 5.0, 6.0, 5.0, 5.5, 7.0);
    BlockCacheEntry expected = new BlockCacheEntry(outline, collision, Material.STONE, 0);

    String encoded = JsonStreamCodecs.encodeToString(BlockCacheEntry.JSON_CODEC, expected);
    BlockCacheEntry decoded = JsonStreamCodecs.decodeFromString(BlockCacheEntry.JSON_CODEC, encoded);
    JsonObject json = new JsonParser().parse(encoded).getAsJsonObject();

    assertEquals("STONE", json.get("type").getAsString());
    assertTrue(json.get("properties").isJsonObject());
    assertTrue(json.get("collisionShape").isJsonArray());
    assertTrue(json.has("outlineShape"));
    assertEquals(expected.type(), decoded.type());
    assertEquals(expected.variantIndex(), decoded.variantIndex());
    assertEquals(expected.collisionShape().elementaryBoxes(), decoded.collisionShape().elementaryBoxes());
  }

  @Test
  void blockStateJsonCodecIgnoresPropertiesWhenReading() {
    BlockCacheEntry decoded = JsonStreamCodecs.decodeFromString(
      BlockCacheEntry.JSON_CODEC,
      "{\"type\":\"STONE\",\"variantIndex\":0,\"collisionShape\":[]," +
        "\"properties\":{\"injected\":true}}"
    );

    assertEquals(Material.STONE, decoded.type());
    assertFalse(decoded.properties().containsKey("injected"));
  }

  @Test
  void jsonPrimitiveMapCodecPreservesPropertyTypesAndSortsNames() {
    Map<String, Comparable<?>> properties = new HashMap<>();
    properties.put("waterlogged", false);
    properties.put("level", 3);
    properties.put("facing", "NORTH");

    String encoded = JsonStreamCodecs.encodeToString(
      JsonStreamCodecs.stringMapCodec(JsonStreamCodecs.JSON_PRIMITIVE),
      properties
    );
    Map<String, Comparable<?>> decoded = JsonStreamCodecs.decodeFromString(
      JsonStreamCodecs.stringMapCodec(JsonStreamCodecs.JSON_PRIMITIVE),
      encoded
    );

    assertEquals("{\"facing\":\"NORTH\",\"level\":3,\"waterlogged\":false}", encoded);
    assertEquals(properties, decoded);
  }

  @Test
  void blockRegionSnapshotJsonCodecRoundTripsPositionsAndStates() {
    PositionedBlockState block = new PositionedBlockState(
      BlockPosition.of(2, 63, -4),
      new BlockCacheEntry(
        BlockShapes.cubeAt(2, 63, -4),
        BlockShapes.cubeAt(2, 63, -4),
        Material.STONE,
        0
      )
    );
    BlockStateRegion expected = new BlockStateRegion(
      BlockPosition.of(0, 61, -6),
      BlockPosition.of(4, 66, -2),
      true,
      Arrays.asList(block)
    );

    String encoded = JsonStreamCodecs.encodeToString(BlockStateRegion.JSON_CODEC, expected);
    BlockStateRegion decoded = JsonStreamCodecs.decodeFromString(
      BlockStateRegion.JSON_CODEC,
      encoded
    );
    JsonObject stateJson = new JsonParser().parse(encoded)
      .getAsJsonObject()
      .getAsJsonArray("nonAirBlocks")
      .get(0)
      .getAsJsonObject()
      .getAsJsonObject("state");

    assertEquals(expected.minInclusive(), decoded.minInclusive());
    assertEquals(expected.maxInclusive(), decoded.maxInclusive());
    assertTrue(decoded.complete());
    assertEquals(1, decoded.nonAirBlocks().size());
    assertEquals(block.position(), decoded.nonAirBlocks().get(0).position());
    BlockCacheEntry decodedState = decoded.nonAirBlocks().get(0).state();
    assertEquals(block.state().type(), decodedState.type());
    assertEquals(block.state().variantIndex(), decodedState.variantIndex());
    assertEquals(
      block.state().collisionShape().elementaryBoxes(),
      decodedState.collisionShape().elementaryBoxes()
    );
    assertEquals(block.state().properties(), decodedState.properties());
    assertTrue(stateJson.has("collisionShape"));
    assertTrue(stateJson.has("outlineShape"));
  }

  @Test
  void maskedMotionToleranceJsonCodecRoundTripsTargets() {
    MaskedMotionTolerance expected = new MaskedMotionTolerance();
    expected.set(-0.25, 0.75);

    String encoded = JsonStreamCodecs.encodeToString(MaskedMotionTolerance.JSON_CODEC, expected);
    MaskedMotionTolerance decoded = JsonStreamCodecs.decodeFromString(
      MaskedMotionTolerance.JSON_CODEC,
      encoded
    );

    assertEquals(expected.motionXTarget(), decoded.motionXTarget());
    assertEquals(expected.motionZTarget(), decoded.motionZTarget());
    assertEquals(expected.motionXTolerance(), decoded.motionXTolerance());
    assertEquals(expected.motionZTolerance(), decoded.motionZTolerance());
    JsonObject json = new JsonParser().parse(encoded).getAsJsonObject();
    assertEquals(expected.motionXTolerance(), json.get("motionXTolerance").getAsDouble());
    assertEquals(expected.motionZTolerance(), json.get("motionZTolerance").getAsDouble());
  }

  @Test
  void movementConfigurationJsonCodecRoundTripsAllState() {
    MovementConfiguration expected = MovementConfiguration.blank()
      .withKeypress(-1, 1)
      .withHandActive(true)
      .withJumped(true)
      .withReduceTicks(2)
      .withSprintingSetTo(true)
      .withReduceBefore(true)
      .denyOverrideToActualMotion()
      .withAlternativeBlockInsideCheck();

    String encoded = JsonStreamCodecs.encodeToString(MovementConfiguration.JSON_CODEC, expected);
    MovementConfiguration decoded = JsonStreamCodecs.decodeFromString(
      MovementConfiguration.JSON_CODEC,
      encoded
    );

    assertEquals(expected.forward(), decoded.forward());
    assertEquals(expected.strafe(), decoded.strafe());
    assertEquals(expected.isHandActive(), decoded.isHandActive());
    assertEquals(expected.isJumping(), decoded.isJumping());
    assertEquals(expected.reduceTicks(), decoded.reduceTicks());
    assertEquals(expected.isSprinting(), decoded.isSprinting());
    assertEquals(expected.reduceBefore(), decoded.reduceBefore());
    assertEquals(expected.overrideEndMotionToActualMotion(), decoded.overrideEndMotionToActualMotion());
    assertEquals(expected.usesAlternateBlockInsideCheck(), decoded.usesAlternateBlockInsideCheck());
    JsonObject json = new JsonParser().parse(encoded).getAsJsonObject();
    assertFalse(json.has("keys"));
    assertFalse(json.has("reducing"));
  }

  @Test
  void movementConfigurationJsonCodecUsesDomainDefaultForMissingOverride() {
    MovementConfiguration decoded = JsonStreamCodecs.decodeFromString(
      MovementConfiguration.JSON_CODEC,
      "{}"
    );

    assertTrue(decoded.overrideEndMotionToActualMotion());
  }

  @Test
  void simulationResultJsonCodecRoundTripsAndPreservesInvalidSentinel() {
    SimulationResult expected = new SimulationResult(
      new Motion(0.25, 0.5, 0.75),
      new Motion(-0.125, 0.25, -0.5),
      new Motion(1, 2, 3),
      true, true, false, true, false, true, true, 0.6
    );

    SimulationResult decoded = JsonStreamCodecs.decodeFromString(
      SimulationResult.JSON_CODEC,
      JsonStreamCodecs.encodeToString(SimulationResult.JSON_CODEC, expected)
    );

    assertTrue(decoded.isValid());
    assertTrue(expected.almostIdenticalTo(decoded));
    assertEquals(expected.actualMotion(), decoded.actualMotion());
    assertFalse(JsonStreamCodecs.decodeFromString(
      SimulationResult.JSON_CODEC,
      JsonStreamCodecs.encodeToString(SimulationResult.JSON_CODEC, SimulationResult.invalid())
    ).isValid());
  }

  @Test
  void simulationResultJsonCodecIgnoresLegacyDebugData() {
    SimulationResult decoded = JsonStreamCodecs.decodeFromString(
      SimulationResult.JSON_CODEC,
      "{\"valid\":true,\"debugData\":{\"motionX\":0.25,\"motionY\":-0.125}," +
        "\"actualMotion\":{\"x\":0.25,\"y\":-0.125,\"z\":0.5}," +
        "\"offsetMotion\":{\"x\":0.25,\"y\":0,\"z\":0.5},\"onGround\":true}"
    );

    assertTrue(decoded.isValid());
    assertEquals(new Motion(0.25, -0.125, 0.5), decoded.actualMotion());
    assertEquals(new Motion(0.25, 0, 0.5), decoded.offsetMotion());
    assertTrue(decoded.onGround());
    JsonObject encoded = new JsonParser().parse(
      JsonStreamCodecs.encodeToString(SimulationResult.JSON_CODEC, decoded)
    ).getAsJsonObject();
    assertFalse(encoded.has("debugData"));
  }

  @Test
  void jsonCodecsPreserveNonFinitePhysicsNumbers() {
    Motion expected = new Motion(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);

    String encoded = JsonStreamCodecs.encodeToString(Motion.JSON_CODEC, expected);
    Motion decoded = JsonStreamCodecs.decodeFromString(Motion.JSON_CODEC, encoded);

    assertEquals("{\"x\":\"NaN\",\"y\":\"Infinity\",\"z\":\"-Infinity\"}", encoded);
    assertTrue(Double.isNaN(decoded.motionX()));
    assertEquals(Double.POSITIVE_INFINITY, decoded.motionY());
    assertEquals(Double.NEGATIVE_INFINITY, decoded.motionZ());
  }
}
