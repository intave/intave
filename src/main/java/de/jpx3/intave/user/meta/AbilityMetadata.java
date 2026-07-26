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
  public static final Predicate<AttributeModifier> EXCLUDE_SPRINT_MODIFIER = modifier -> modifier.id() == null ?
    !"662A6B8D-DA3E-4C1C-8813-96EA6097278D".equalsIgnoreCase(modifier.key().path()) && !"minecraft:sprinting".equalsIgnoreCase(modifier.key().fullKey())
    : !modifier.id().equals(SPEED_MODIFIER_SPRINTING_UUID);


  private final Player player;
  private boolean flying;
  private boolean allowFlying;
  public boolean disabledFlying;

  private AbilityTracker.GameMode gameMode = NOT_SET;
  private AbilityTracker.GameMode pendingGameMode = NOT_SET;

  private float flySpeed = 0.05f;

	private final AtomicReference<Map<String, Attribute>> attributes = new AtomicReference<>(new HashMap<>());
  private final AtomicReference<Map<String, List<AttributeModifier>>> attributeModifiers = new AtomicReference<>(new HashMap<>());
  private double scaleCache = Double.NEGATIVE_INFINITY;
  private double jumpStrengthCache = Double.NEGATIVE_INFINITY;

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
      setupAttribute("generic.jump_strength", 0.42f);
    }
    if (MinecraftVersions.VER1_21.atOrAbove()) {
      setupAttribute("generic.scale", 1.0D);
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
    scaleCache = Double.NEGATIVE_INFINITY;
    jumpStrengthCache = Double.NEGATIVE_INFINITY;
  }

  public double scale() {
    if (Double.isInfinite(scaleCache)) {
      double newScaleCache = attributeValue("generic.scale");
      scaleCache = newScaleCache;
      return newScaleCache;
    }
    return scaleCache;
  }

  public double jumpStrength() {
    if (Double.isInfinite(jumpStrengthCache)) {
      double newJumpStrengthCache = attributeValue("generic.jump_strength");
      jumpStrengthCache = newJumpStrengthCache;
      return newJumpStrengthCache;
    }
    return jumpStrengthCache;
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

  private static final boolean KEY_WRAPPED;
  private static final Map<String, String> REMAP;

  static {
    KEY_WRAPPED = MinecraftVersions.VER1_16_0.atOrAbove();
    Map<String, String> remap = new HashMap<>();
    if (MinecraftVersions.VER1_21_3.atOrAbove()) {
      remap.put("generic.maxHealth", "max_health");
      remap.put("generic.followRange", "follow_range");
      remap.put("generic.knockbackResistance", "knockback_resistance");
      remap.put("generic.movementSpeed", "movement_speed");
      remap.put("generic.attackDamage", "attack_damage");
      remap.put("generic.attackSpeed", "attack_speed");
      remap.put("generic.armorToughness", "armor_toughness");
      remap.put("generic.attackKnockback", "attack_knockback");
      remap.put("generic.jump_strength", "jump_strength");
      remap.put("horse.jumpStrength", "jump_strength");
      remap.put("horse.jump_strength", "jump_strength");
      remap.put("zombie.spawnReinforcements", "spawn_reinforcements");
      remap.put("generic.scale", "scale");
      remap.put("player.sneaking_speed", "sneaking_speed");
    } else {
      remap.put("generic.maxHealth", "generic.max_health");
      remap.put("generic.followRange", "generic.follow_range");
      remap.put("generic.knockbackResistance", "generic.knockback_resistance");
      remap.put("generic.movementSpeed", "generic.movement_speed");
      remap.put("generic.attackDamage", "generic.attack_damage");
      remap.put("generic.attackSpeed", "generic.attack_speed");
      remap.put("generic.armorToughness", "generic.armor_toughness");
      remap.put("generic.attackKnockback", "generic.attack_knockback");
      remap.put("horse.jumpStrength", "horse.jump_strength");
      remap.put("zombie.spawnReinforcements", "zombie.spawn_reinforcements");
    }
    REMAP = ImmutableMap.copyOf(remap);
  }

  private String keyTranslation(String key) {
    return KEY_WRAPPED ? REMAP.getOrDefault(key, key) : key;
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
