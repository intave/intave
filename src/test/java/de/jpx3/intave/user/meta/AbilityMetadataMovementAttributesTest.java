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

package de.jpx3.intave.user.meta;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.player.attribute.AttributeModifier;
import de.jpx3.intave.share.MinecraftKey;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class AbilityMetadataMovementAttributesTest {
  private static final UUID LEGACY_SPRINT_MODIFIER_ID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");

  @BeforeEach
  void setUp() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
  }

  @AfterEach
  void tearDown() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
  }

  @Test
  void registersStepHeightAt1205() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_20_5);

    AbilityMetadata abilities = abilities();

    assertEquals(0.6D, abilities.stepHeight());
    assertTrue(abilities.hasAttribute("generic.step_height"));
    assertFalse(abilities.hasAttribute("generic.movement_efficiency"));
    assertFalse(abilities.hasAttribute("generic.water_movement_efficiency"));
  }

  @Test
  void doesNotRegisterStepHeightBefore1205() {
    MinecraftVersion.setCurrent(new MinecraftVersion("1.20.4"));

    AbilityMetadata abilities = abilities();

    assertFalse(abilities.hasAttribute("generic.step_height"));
    assertEquals(0.6D, abilities.stepHeight());
  }

  @Test
  void registersEfficiencyAttributesAt121() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21);

    AbilityMetadata abilities = abilities();

    assertTrue(abilities.hasAttribute("generic.movement_efficiency"));
    assertTrue(abilities.hasAttribute("generic.water_movement_efficiency"));
    assertEquals(0.0D, abilities.movementEfficiency());
    assertEquals(0.0D, abilities.waterMovementEfficiency());
  }

  @Test
  void remapsMovementAttributesAt1212() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_2);

    AbilityMetadata abilities = abilities();
    Attribute stepHeight = abilities.findAttribute("generic.step_height");
    Attribute movementEfficiency = abilities.findAttribute("generic.movement_efficiency");
    Attribute waterMovementEfficiency = abilities.findAttribute("generic.water_movement_efficiency");

    assertEquals("step_height", stepHeight.attributeKey());
    assertEquals("movement_efficiency", movementEfficiency.attributeKey());
    assertEquals("water_movement_efficiency", waterMovementEfficiency.attributeKey());
    assertSame(stepHeight, abilities.findAttribute("step_height"));
    assertSame(movementEfficiency, abilities.findAttribute("movement_efficiency"));
    assertSame(waterMovementEfficiency, abilities.findAttribute("water_movement_efficiency"));
  }

  @Test
  void exposesMovementAttributesWithClientBounds() {
    AbilityMetadata abilities = abilities();

    abilities.modifyBaseValue("step_height", 20.0D);
    abilities.modifyBaseValue("movement_efficiency", -1.0D);
    abilities.modifyBaseValue("water_movement_efficiency", 2.0D);
    assertEquals(10.0D, abilities.stepHeight());
    assertEquals(0.0D, abilities.movementEfficiency());
    assertEquals(1.0D, abilities.waterMovementEfficiency());

    abilities.modifyBaseValue("step_height", Double.NaN);
    abilities.modifyBaseValue("movement_efficiency", Double.NaN);
    abilities.modifyBaseValue("water_movement_efficiency", Double.NaN);
    assertEquals(0.0D, abilities.stepHeight());
    assertEquals(0.0D, abilities.movementEfficiency());
    assertEquals(0.0D, abilities.waterMovementEfficiency());
  }

  @Test
  void registers262MovementAttributesUnderTheirModernKeys() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_2);

    AbilityMetadata abilities = abilities();
    Attribute airDrag = abilities.findAttribute("generic.air_drag_modifier");
    Attribute bounciness = abilities.findAttribute("generic.bounciness");
    Attribute friction = abilities.findAttribute("generic.friction_modifier");

    assertEquals("air_drag_modifier", airDrag.attributeKey());
    assertEquals("bounciness", bounciness.attributeKey());
    assertEquals("friction_modifier", friction.attributeKey());
    assertSame(airDrag, abilities.findAttribute("air_drag_modifier"));
    assertSame(bounciness, abilities.findAttribute("bounciness"));
    assertSame(friction, abilities.findAttribute("friction_modifier"));
    assertEquals(1.0D, abilities.airDragModifier());
    assertEquals(0.0D, abilities.bounciness());
    assertEquals(1.0D, abilities.frictionModifier());
  }

  @Test
  void keeps262MovementAttributeDefaultsWhenTheyAreUnavailable() {
    MinecraftVersion.setCurrent(new MinecraftVersion("26.1.2"));

    AbilityMetadata abilities = abilities();

    assertFalse(abilities.hasAttribute("air_drag_modifier"));
    assertFalse(abilities.hasAttribute("bounciness"));
    assertFalse(abilities.hasAttribute("friction_modifier"));
    assertEquals(1.0D, abilities.airDragModifier());
    assertEquals(0.0D, abilities.bounciness());
    assertEquals(1.0D, abilities.frictionModifier());
  }

  @Test
  void exposes262MovementAttributesWithClientBounds() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_2);
    AbilityMetadata abilities = abilities();

    abilities.modifyBaseValue("air_drag_modifier", 4096.0D);
    abilities.modifyBaseValue("bounciness", 2.0D);
    abilities.modifyBaseValue("friction_modifier", -1.0D);
    assertEquals(2048.0D, abilities.airDragModifier());
    assertEquals(1.0D, abilities.bounciness());
    assertEquals(0.0D, abilities.frictionModifier());

    abilities.modifyBaseValue("air_drag_modifier", Double.NaN);
    abilities.modifyBaseValue("bounciness", Double.NaN);
    abilities.modifyBaseValue("friction_modifier", Double.NaN);
    assertEquals(0.0D, abilities.airDragModifier());
    assertEquals(0.0D, abilities.bounciness());
    assertEquals(0.0D, abilities.frictionModifier());
  }

  @Test
  void oldRecordingSnapshotsRetain262IdentityDefaults() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_2);
    AbilityMetadata abilities = abilities();

    abilities.replaceAttributeSnapshot(Collections.emptyMap());

    assertEquals(1.0D, abilities.airDragModifier());
    assertEquals(0.0D, abilities.bounciness());
    assertEquals(1.0D, abilities.frictionModifier());
  }

  @Test
  void detectsLegacySprintModifierByIdentity() {
    AttributeModifier sprinting = new AttributeModifier(
      new MinecraftKey("intave", "custom_modifier"),
      LEGACY_SPRINT_MODIFIER_ID,
      "Sprinting speed boost",
      AttributeModifier.Operation.ADD_PERCENTAGE,
      0.30000001192092896D
    );

	  assertNotEquals(sprinting, MovementMetadata.SPRINTING_MODIFIER);
    assertTrue(AbilityMetadata.hasSprintModifier(List.of(sprinting)));
  }

  @Test
  void detectsModernSprintModifierByKey() {
    AttributeModifier sprinting = new AttributeModifier(
      new MinecraftKey("minecraft", "sprinting"),
      null,
      null,
      AttributeModifier.Operation.ADD_PERCENTAGE,
      0.3D
    );

    assertTrue(AbilityMetadata.hasSprintModifier(List.of(sprinting)));
  }

  @Test
  void rejectsUnrelatedMovementSpeedModifier() {
    AttributeModifier unrelated = new AttributeModifier(
      new MinecraftKey("minecraft", "powder_snow"),
      UUID.randomUUID(),
      "Powder snow slow",
      AttributeModifier.Operation.ADD_NUMBER,
      -0.025D
    );

    assertFalse(AbilityMetadata.hasSprintModifier(List.of(unrelated)));
  }

  @Test
  void onlyModernClientsUseTrackedPlayerStepHeight() {
    User modern = user(ProtocolMetadata.VER_1_20_5);
    modern.meta().abilities().modifyBaseValue("step_height", 1.25D);
    User legacy = user(ProtocolMetadata.VER_1_20_3);
    legacy.meta().abilities().modifyBaseValue("step_height", 1.25D);

    assertEquals(1.25F, Simulators.PLAYER.stepHeight(modern));
    assertEquals(0.6F, Simulators.PLAYER.stepHeight(legacy));
    assertEquals(0.0F, Simulators.BOAT.stepHeight(modern));
    assertEquals(0.6F, Simulators.HORSE.stepHeight(modern));
  }

  private static AbilityMetadata abilities() {
    return new AbilityMetadata(FakePlayerFactory.createPlayer((ignored, arguments) -> null));
  }

  private static User user(int protocolVersion) {
    World world = FakeWorldFactory.createWorld((methodName, arguments) -> switch (methodName) {
      case "isChunkLoaded", "isChunkInUse" -> true;
      case "isThundering", "hasStorm" -> false;
      default -> null;
    });
    Location location = new Location(world, 0.0D, 0.0D, 0.0D);
    Player player = FakePlayerFactory.createPlayer((methodName, arguments) -> switch (methodName) {
      case "getLocation" -> location;
      case "getWorld" -> world;
      default -> null;
    });
    MockFullBlockStaticPlane blocks = new MockFullBlockStaticPlane();
    return UserFactory.createTestUserFor(player, (ignored, key) -> switch (key) {
      case "blockCache" -> blocks;
      case "protocolVersion" -> protocolVersion;
      default -> null;
    });
  }
}
