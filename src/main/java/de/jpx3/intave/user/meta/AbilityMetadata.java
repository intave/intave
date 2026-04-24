package de.jpx3.intave.user.meta;

import com.github.retrooper.packetevents.protocol.attribute.Attribute;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes.Property;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes.PropertyModifier;
import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static de.jpx3.intave.module.tracker.player.AbilityTracker.GameMode.NOT_SET;

public final class AbilityMetadata {
  private static final UUID SPEED_MODIFIER_SPRINTING_UUID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
  public static final Predicate<PropertyModifier> EXCLUDE_SPRINT_MODIFIER =
    modifier -> !MovementMetadata.isSprintingModifier(modifier);


  private final Player player;
  private boolean flying;
  private boolean allowFlying;

  private AbilityTracker.GameMode gameMode = NOT_SET;
  private AbilityTracker.GameMode pendingGameMode = NOT_SET;

  private float flySpeed = 0.05f;
  private float walkSpeed = 0.1f;

  private final Map<String, Property> attributes = new ConcurrentHashMap<>();
  private final Map<String, List<PropertyModifier>> attributeModifiers = new ConcurrentHashMap<>();

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

      this.walkSpeed = player.getWalkSpeed() / 2.0f;
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
//      Thread.dumpStack();
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
    if (MinecraftVersions.VER1_21.atOrAbove()) {
      setupAttribute("generic.scale", 1.0D);
    }
  }

  private void setupAttribute(String name, double baseValue) {
    name = keyTranslation(name);
    try {
      Attribute attributeType = Attributes.getByName(name);
      if (attributeType == null) {
        IntaveLogger.logger().warn("Unable to resolve attribute " + name + " for player " + player.getName());
        return;
      }
      Property attribute = new Property(attributeType, baseValue, new ArrayList<>());
      attributes.put(name, reduceNumberPrecision(attribute));
      attributeModifiers.put(name, new CopyOnWriteArrayList<>());
    } catch (Exception e) {
      IntaveLogger.logger().error("Unable to setup attribute " + name + " for player " + player.getName());
      e.printStackTrace();
    }
  }

  public double attributeValue(String key) {
    return attributeValue(key, x -> true);
  }

  public double attributeValue(String key, Predicate<? super PropertyModifier> filter) {
    key = keyTranslation(key);
    Property attribute = attributes.get(key);
    List<PropertyModifier> attributeModifiers = this.attributeModifiers.get(key);
    if (attribute == null || attributeModifiers == null) {
      return Double.NaN;
    }
    double x = attribute.getValue();
    double y = 0.0;
    // Attribute phase application mirrors vanilla's modifier ordering.
    for(int phase = 0; phase < 3; ++phase) {
      for (PropertyModifier modifier : attributeModifiers) {
        if (!filter.test(modifier)) {
          continue;
        }
        if (modifier.getOperation().ordinal() == phase) {
          switch (phase) {
            case 0:
              x += modifier.getAmount();
              break;
            case 1:
              y += x * modifier.getAmount();
              break;
            case 2:
              y *= 1.0 + modifier.getAmount();
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

  public List<PropertyModifier> modifiersOf(Property attribute) {
    return attributeModifiers.get(attributeKey(attribute));
  }

  private Property reduceNumberPrecision(Property input) {
    double baseValue = reducePrecision(input.getValue());
    return new Property(input.getAttribute(), baseValue, new ArrayList<>(input.getModifiers()));
  }

  private static final double REDUCE_APPLIER = 1000d;

  private double reducePrecision(double input) {
    return Math.round(input * REDUCE_APPLIER) / REDUCE_APPLIER;
  }

  public Property findAttribute(String key) {
    key = keyTranslation(key);
    Property direct = attributes.get(key);
    if (direct != null) {
      return direct;
    }
    Attribute attributeType = Attributes.getByName(key);
    if (attributeType == null) {
      return null;
    }
    for (Property property : attributes.values()) {
      if (property.getAttribute() == attributeType || property.getAttribute().equals(attributeType)) {
        return property;
      }
    }
    return null;
  }

  public List<? extends String> attributeKeys() {
    return new ArrayList<>(attributes.keySet());
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
      remap.put("horse.jumpStrength", "jump_strength");
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

  private String attributeKey(Property attribute) {
    for (Map.Entry<String, Property> entry : attributes.entrySet()) {
      Property value = entry.getValue();
      if (value == attribute || value.getAttribute() == attribute.getAttribute() || value.getAttribute().equals(attribute.getAttribute())) {
        return entry.getKey();
      }
    }
    return keyTranslation(attribute.getKey());
  }

  public void modifyBaseValue(String key, double baseValue) {
    key = keyTranslation(key);
    Property attribute = findAttribute(key);
    if (attribute != null) {
      attribute.setValue(reducePrecision(baseValue));
      attributes.put(key, attribute);
      List<PropertyModifier> modifiers = modifiersOf(attribute);
      attributeModifiers.remove(key);
      attributeModifiers.put(key, new ArrayList<>(modifiers));
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

  public void setPendingGameMode(AbilityTracker.GameMode pendingGameMode) {
    this.pendingGameMode = pendingGameMode;
  }
}
