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

import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.player.attribute.AttributeModifier;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static de.jpx3.intave.module.tracker.player.AbilityTracker.GameMode.NOT_SET;

public final class AbilityMetadata {
  private static final UUID SPEED_MODIFIER_SPRINTING_UUID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
  public static final Predicate<AttributeModifier> EXCLUDE_SPRINT_MODIFIER = modifier -> !isSprintModifier(modifier);

  public static boolean isSprintModifier(AttributeModifier modifier) {
    if (SPEED_MODIFIER_SPRINTING_UUID.equals(modifier.id())) {
      return true;
    }
    if (modifier.key() == null) {
      return false;
    }
    return "662A6B8D-DA3E-4C1C-8813-96EA6097278D".equalsIgnoreCase(modifier.key().path())
      || "minecraft:sprinting".equalsIgnoreCase(modifier.key().fullKey());
  }

  public static boolean hasSprintModifier(Collection<AttributeModifier> modifiers) {
    return modifiers.stream().anyMatch(AbilityMetadata::isSprintModifier);
  }


  private final Player player;
  private boolean flying;
  private boolean allowFlying;
  public boolean disabledFlying;

  private AbilityTracker.GameMode gameMode = NOT_SET;
  private AbilityTracker.GameMode pendingGameMode = NOT_SET;

  private float flySpeed = 0.05f;

	private final AtomicReference<Map<String, Attribute>> attributes = new AtomicReference<>(new HashMap<>());
  private final AtomicReference<Map<String, List<AttributeModifier>>> attributeModifiers = new AtomicReference<>(new HashMap<>());
  private double scaleCache;
  private boolean scaleCacheValid;
  private double jumpStrengthCache;
  private boolean jumpStrengthCacheValid;

  public float unsynchronizedHealth;
  public float health;
  public int foodLevel;
  public int ticksToLastHealthUpdate;
  public boolean hasViewEntity;

  public AbilityMetadata(Player player) {
    this.player = player;
    boolean hasPlayer = (player != null);
    if (hasPlayer) {
      this.allowFlying = player.getAllowFlight();
      this.flying = player.isFlying();
      this.health = (float) player.getHealth();
      this.unsynchronizedHealth = this.health;
      this.foodLevel = player.getFoodLevel();
      setupDefaultGameMode(player.getGameMode());

	    this.flySpeed = player.getFlySpeed() / 2.0f;

      setupAttributes();
    } else {
      this.allowFlying = this.flying = false;
      this.health = 20.0f;
      this.unsynchronizedHealth = this.health;
    }
  }

  private void setupDefaultGameMode(GameMode gameMode) {
    if (gameMode == null) {
      IntaveLogger.logger().warn("Player " + player.getName() + " has no game mode set, this is quite dangerous and may lead to unexpected behaviour.");
    }
    int gameModeValue = gameMode == null ? -1 : gameMode.getValue();
    this.gameMode = Arrays.stream(AbilityTracker.GameMode.values())
      .filter(mode -> mode.id() == gameModeValue)
      .findFirst().orElse(NOT_SET);
    this.pendingGameMode = this.gameMode;
  }

  public void setupAttributes() {
    boolean atLeastMinecraft16 = MinecraftVersions.VER1_16_0.atOrAbove();
    setupAttribute("generic.movementSpeed", atLeastMinecraft16 ? (double) 0.1F : 0.1D);
    setupAttribute("generic.maxHealth", 20.0D);
    setupAttribute("generic.knockbackResistance", 0.0D);
    setupAttribute("generic.attackDamage", 1.0D);
    if (MinecraftVersions.VER1_19.atOrAbove()) {
      setupAttribute("player.sneaking_speed", 0.3D);
    }
    if (MinecraftVersions.VER1_20_5.atOrAbove()) {
      setupAttribute("player.block_interaction_range", 4.5D);
      setupAttribute("player.entity_interaction_range", 3.0D);
      setupAttribute("generic.gravity", 0.08D);
      setupAttribute("generic.jump_strength", 0.42f);
      setupAttribute("generic.step_height", 0.6D);
    }
    if (MinecraftVersions.VER1_21.atOrAbove()) {
      setupAttribute("generic.scale", 1.0D);
      setupAttribute("generic.movement_efficiency", 0.0D);
      setupAttribute("generic.water_movement_efficiency", 0.0D);
    }
    if (MinecraftVersions.VER26_2.atOrAbove()) {
      setupAttribute("generic.air_drag_modifier", 1.0D);
      setupAttribute("generic.bounciness", 0.0D);
      setupAttribute("generic.friction_modifier", 1.0D);
    }
  }

  private void setupAttribute(String name, double baseValue) {
    name = keyTranslation(name);
    try {
      Attribute attribute = Attribute.newBuilder()
        .withAttributeKey(name).withBaseValue(baseValue).build();
      String finalName = name;
      attributes.updateAndGet(oldMap -> {
        Map<String, Attribute> newMap = new HashMap<>(oldMap);
        newMap.put(finalName, reduceNumberPrecision(attribute));
        return newMap;
      });
      attributeModifiers.updateAndGet(oldMap -> {
        Map<String, List<AttributeModifier>> newMap = new HashMap<>(oldMap);
        newMap.put(finalName, new CopyOnWriteArrayList<>());
        return newMap;
      });
      clearAttributeCaches();
    } catch (Exception e) {
      IntaveLogger.logger().error("Unable to setup attribute " + name + " for player " + player.getName());
      e.printStackTrace();
    }
  }

  private void clearAttributeCaches() {
    scaleCacheValid = false;
    jumpStrengthCacheValid = false;
  }

  public double scale() {
    if (!scaleCacheValid) {
      scaleCache = attributeValue("generic.scale");
      scaleCacheValid = true;
    }
    return scaleCache;
  }

  public double jumpStrength() {
    if (!jumpStrengthCacheValid) {
      jumpStrengthCache = attributeValue("generic.jump_strength");
      jumpStrengthCacheValid = true;
    }
    return jumpStrengthCache;
  }

  public double entityInteractionRange() {
    String attributeKey = "player.entity_interaction_range";
    if (findAttribute(attributeKey) == null) {
      return inGameMode(GameMode.CREATIVE) ? 5.0D : 3.0D;
    }

    double value = attributeValue(attributeKey);
    return Double.isNaN(value) ? 0.0D : Math.max(0.0D, Math.min(64.0D, value));
  }

  public double blockInteractionRange() {
    String attributeKey = "player.block_interaction_range";
    if (findAttribute(attributeKey) == null) {
      return inGameMode(GameMode.CREATIVE) ? 5.0D : 4.5D;
    }

    double value = attributeValue(attributeKey);
    return Double.isNaN(value) ? 0.0D : Math.max(0.0D, Math.min(64.0D, value));
  }

  public double gravity() {
    String attributeKey = "generic.gravity";
    if (findAttribute(attributeKey) == null) {
      return 0.08D;
    }

    double value = attributeValue(attributeKey);
    return Double.isNaN(value) ? -1.0D : Math.max(-1.0D, Math.min(1.0D, value));
  }

  public double stepHeight() {
    String attributeKey = "generic.step_height";
    if (findAttribute(attributeKey) == null) {
      return 0.6D;
    }

    double value = attributeValue(attributeKey);
    return Double.isNaN(value) ? 0.0D : Math.max(0.0D, Math.min(10.0D, value));
  }

  public double movementEfficiency() {
    return unitIntervalAttributeValue("generic.movement_efficiency");
  }

  public double waterMovementEfficiency() {
    return unitIntervalAttributeValue("generic.water_movement_efficiency");
  }

  public double airDragModifier() {
    return boundedAttributeValue("generic.air_drag_modifier", 1.0D, 0.0D, 2048.0D);
  }

  public double bounciness() {
    return boundedAttributeValue("generic.bounciness", 0.0D, 0.0D, 1.0D);
  }

  public double frictionModifier() {
    return boundedAttributeValue("generic.friction_modifier", 1.0D, 0.0D, 2048.0D);
  }

  private double unitIntervalAttributeValue(String attributeKey) {
    return boundedAttributeValue(attributeKey, 0.0D, 0.0D, 1.0D);
  }

  private double boundedAttributeValue(
    String attributeKey,
    double defaultValue,
    double minValue,
    double maxValue
  ) {
    if (findAttribute(attributeKey) == null) {
      return defaultValue;
    }

    double value = attributeValue(attributeKey);
    return Double.isNaN(value) ? minValue : Math.max(minValue, Math.min(maxValue, value));
  }

  public double attributeValue(String key) {
    return attributeValue(key, x -> true);
  }

  public double attributeValue(String key, Predicate<? super AttributeModifier> filter) {
    key = keyTranslation(key);
    Attribute attribute = attributes.get().get(key);
    List<AttributeModifier> attributeModifiers = this.attributeModifiers.get().get(key);
    if (attribute == null || attributeModifiers == null) {
      return Double.NaN;
    }
    double x = attribute.baseValue();
    double y = 0.0;
    // ProtocolLib code pasted,
    for(int phase = 0; phase < 3; ++phase) {
      for (AttributeModifier modifier : attributeModifiers) {
        if (!filter.test(modifier)) {
          continue;
        }
        if (modifier.operation().getId() == phase) {
          switch (phase) {
            case 0:
              x += modifier.amount();
              break;
            case 1:
              y += x * modifier.amount();
              break;
            case 2:
              y *= 1.0 + modifier.amount();
              break;
          }
        }
      }
      if (phase == 0) {
        y = x;
      }
    }
    return y;
  }

  public List<AttributeModifier> modifiersOf(Attribute attribute) {
    return attributeModifiers.get().get(keyTranslation(attribute.attributeKey()));
  }

  public Map<String, Attribute> attributeSnapshot() {
    Map<String, Attribute> snapshot = new HashMap<>();
    Map<String, Attribute> currentAttributes = attributes.get();
    Map<String, List<AttributeModifier>> currentModifiers = attributeModifiers.get();
    currentAttributes.forEach((key, attribute) -> snapshot.put(
      key,
      Attribute.newBuilder(attribute)
        .withAttributeModifiers(new HashSet<>(currentModifiers.getOrDefault(key, Collections.emptyList())))
        .build()
    ));
    return snapshot;
  }

  public void replaceAttributeSnapshot(Map<String, Attribute> snapshot) {
    Map<String, Attribute> newAttributes = new HashMap<>();
    Map<String, List<AttributeModifier>> newModifiers = new HashMap<>();
    snapshot.forEach((key, attribute) -> {
      newAttributes.put(key, Attribute.newBuilder(attribute).withAttributeModifiers(Collections.emptySet()).build());
      newModifiers.put(key, new CopyOnWriteArrayList<>(attribute.modifiers()));
    });
    attributes.set(newAttributes);
    attributeModifiers.set(newModifiers);
    clearAttributeCaches();
  }

  private Attribute reduceNumberPrecision(Attribute input) {
    double baseValue = reducePrecision(input.baseValue());
    return Attribute.newBuilder(input).withBaseValue(baseValue).build();
  }

  private static final double REDUCE_APPLIER = 1000d;

  private double reducePrecision(double input) {
    return Math.round(input * REDUCE_APPLIER) / REDUCE_APPLIER;
  }

  public Attribute findAttribute(String key) {
    Attribute attribute = attributes.get().get(keyTranslation(key));
    if (attribute == null) {
      attribute = attributes.get().get(keyTranslation("generic." + key));
    }
    return attribute;
  }

  public boolean hasAttribute(String key) {
    return findAttribute(key) != null;
  }

  private static final Map<String, String> LEGACY_WRAPPED_KEY_REMAP;
  private static final Map<String, String> MODERN_WRAPPED_KEY_REMAP;

  static {
    Map<String, String> legacyRemap = new HashMap<>();
    legacyRemap.put("generic.maxHealth", "generic.max_health");
    legacyRemap.put("generic.followRange", "generic.follow_range");
    legacyRemap.put("generic.knockbackResistance", "generic.knockback_resistance");
    legacyRemap.put("generic.movementSpeed", "generic.movement_speed");
    legacyRemap.put("generic.attackDamage", "generic.attack_damage");
    legacyRemap.put("generic.attackSpeed", "generic.attack_speed");
    legacyRemap.put("generic.armorToughness", "generic.armor_toughness");
    legacyRemap.put("generic.attackKnockback", "generic.attack_knockback");
    legacyRemap.put("horse.jumpStrength", "horse.jump_strength");
    legacyRemap.put("zombie.spawnReinforcements", "zombie.spawn_reinforcements");
    LEGACY_WRAPPED_KEY_REMAP = ImmutableMap.copyOf(legacyRemap);

    Map<String, String> modernRemap = new HashMap<>();
    modernRemap.put("generic.maxHealth", "max_health");
    modernRemap.put("generic.followRange", "follow_range");
    modernRemap.put("generic.knockbackResistance", "knockback_resistance");
    modernRemap.put("generic.movementSpeed", "movement_speed");
    modernRemap.put("generic.attackDamage", "attack_damage");
    modernRemap.put("generic.attackSpeed", "attack_speed");
    modernRemap.put("generic.armorToughness", "armor_toughness");
    modernRemap.put("generic.attackKnockback", "attack_knockback");
    modernRemap.put("generic.jump_strength", "jump_strength");
    modernRemap.put("horse.jumpStrength", "jump_strength");
    modernRemap.put("horse.jump_strength", "jump_strength");
    modernRemap.put("zombie.spawnReinforcements", "spawn_reinforcements");
    modernRemap.put("generic.gravity", "gravity");
    modernRemap.put("generic.scale", "scale");
    modernRemap.put("generic.step_height", "step_height");
    modernRemap.put("generic.movement_efficiency", "movement_efficiency");
    modernRemap.put("generic.water_movement_efficiency", "water_movement_efficiency");
    modernRemap.put("generic.air_drag_modifier", "air_drag_modifier");
    modernRemap.put("generic.bounciness", "bounciness");
    modernRemap.put("generic.friction_modifier", "friction_modifier");
    modernRemap.put("player.sneaking_speed", "sneaking_speed");
    modernRemap.put("player.block_interaction_range", "block_interaction_range");
    modernRemap.put("player.entity_interaction_range", "entity_interaction_range");
    MODERN_WRAPPED_KEY_REMAP = ImmutableMap.copyOf(modernRemap);
  }

  private String keyTranslation(String key) {
    if (!MinecraftVersions.VER1_16_0.atOrAbove()) {
      return key;
    }
    Map<String, String> remap = MinecraftVersions.VER1_21_2.atOrAbove()
      ? MODERN_WRAPPED_KEY_REMAP
      : LEGACY_WRAPPED_KEY_REMAP;
    return remap.getOrDefault(key, key);
  }

  public void modifyBaseValue(String key, double baseValue) {
    key = keyTranslation(key);
    Attribute attribute = findAttribute(key);
    if (attribute != null) {
      String finalKey = key;
      attributes.updateAndGet(oldMap -> {
        Map<String, Attribute> newMap = new HashMap<>(oldMap);
        newMap.put(finalKey, Attribute.newBuilder(attribute).withBaseValue(baseValue).build());
        return newMap;
      });
      List<AttributeModifier> modifiers = modifiersOf(attribute);
      attributeModifiers.updateAndGet(oldMap -> {
        Map<String, List<AttributeModifier>> newMap = new HashMap<>(oldMap);
        newMap.put(finalKey, new ArrayList<>(modifiers));
        return newMap;
      });
      clearAttributeCaches();
    }
  }

  public boolean inGameModeIncludePending(AbilityTracker.GameMode gameMode) {
    return this.gameMode == gameMode || this.pendingGameMode == gameMode;
  }

  public boolean ignoringMovementPackets() {
    return inGameModeIncludePending(AbilityTracker.GameMode.SPECTATOR) || hasViewEntity;
  }

  public boolean inGameMode(GameMode gameMode) {
    return this.gameMode.id() == gameMode.getValue();
  }

  public boolean inGameMode(AbilityTracker.GameMode gameMode) {
    return this.gameMode == gameMode;
  }

  public boolean probablyFlying() {
    return flying || player.getAllowFlight();
  }

  public boolean flying() {
    return flying;
  }

  public boolean allowFlying() {
    return allowFlying;
  }

  public float flySpeed() {
    return flySpeed;
  }

  public void setFlying(boolean flying) {
    this.flying = flying;
  }

  public void setAllowFlying(boolean allowFlying) {
    this.allowFlying = allowFlying;
  }

  public void setWalkSpeed(float walkSpeed) {
// "walkspeed" is just baseline value for fov, not actual speed
//    modifyBaseValue("generic.movementSpeed", walkSpeed);
  }

  public void setFlySpeed(float flySpeed) {
    this.flySpeed = flySpeed;
  }

  public void setGameMode(AbilityTracker.GameMode gameMode) {
    if (this.gameMode == AbilityTracker.GameMode.SPECTATOR && gameMode == AbilityTracker.GameMode.CREATIVE) {
      setAllowFlying(true);
      setFlying(true);
    }
    this.gameMode = gameMode;
  }

  public void tickComplete() {
    ticksToLastHealthUpdate++;

    if (disabledFlying || !allowFlying()) {
      setFlying(false);
      disabledFlying = false;
    }
  }

  public void setPendingGameMode(AbilityTracker.GameMode pendingGameMode) {
    this.pendingGameMode = pendingGameMode;
  }
}
