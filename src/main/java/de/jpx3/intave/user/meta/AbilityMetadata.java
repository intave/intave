package de.jpx3.intave.user.meta;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import com.comphenix.protocol.wrappers.WrappedAttributeModifier;
import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static de.jpx3.intave.module.tracker.player.AbilityTracker.GameMode.NOT_SET;

@Relocate
public final class AbilityMetadata {
  private static final UUID SPEED_MODIFIER_SPRINTING_UUID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
  public static final Predicate<WrappedAttributeModifier> EXCLUDE_SPRINT_MODIFIER = modifier -> !modifier.getUUID().equals(SPEED_MODIFIER_SPRINTING_UUID);

  private final Player player;
  private boolean flying;
  private boolean allowFlying;

  private AbilityTracker.GameMode gameMode = NOT_SET;
  private AbilityTracker.GameMode pendingGameMode = NOT_SET;

  private float flySpeed = 0.05f;
  private float walkSpeed = 0.1f;

  private final Map<String, WrappedAttribute> attributes = new ConcurrentHashMap<>();
  private final Map<String, List<WrappedAttributeModifier>> attributeModifiers = new ConcurrentHashMap<>();

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
  }

  private void setupAttribute(String name, double baseValue) {
    name = keyTranslation(name);
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.UPDATE_ATTRIBUTES);
    WrappedAttribute attribute = WrappedAttribute.newBuilder()
      .attributeKey(name).baseValue(baseValue).packet(packet).build();
    attributes.put(name, reduceNumberPrecision(attribute));
    attributeModifiers.put(name, new CopyOnWriteArrayList<>());
  }

  public double attributeValue(String key) {
    return attributeValue(key, x -> true);
  }

  public double attributeValue(String key, Predicate<? super WrappedAttributeModifier> filter) {
    key = keyTranslation(key);
    for (Map.Entry<String, List<WrappedAttributeModifier>> wrappedAttributeListEntry : attributeModifiers.entrySet()) {
      WrappedAttribute attribute = attributes.get(wrappedAttributeListEntry.getKey());
      if (attribute == null) {
        continue;
      }
      if (keyTranslation(attribute.getAttributeKey()).equals(key)) {
        List<WrappedAttributeModifier> modifiers = wrappedAttributeListEntry.getValue();
        if (!modifiers.isEmpty()) {
          modifiers = new ArrayList<>(modifiers);
          modifiers.removeIf(filter.negate());
          attribute = attribute.withModifiers(modifiers);
        }
        return attribute.getFinalValue();
      }
    }
    return Double.NaN;
  }

  public List<WrappedAttributeModifier> modifiersOf(WrappedAttribute attribute) {
    return attributeModifiers.get(keyTranslation(attribute.getAttributeKey()));
  }

  private WrappedAttribute reduceNumberPrecision(WrappedAttribute input) {
    double baseValue = reducePrecision(input.getBaseValue());
    return WrappedAttribute.newBuilder(input).baseValue(baseValue).build();
  }

  private static final double REDUCE_APPLIER = 1000d;

  private double reducePrecision(double input) {
    return Math.round(input * REDUCE_APPLIER) / REDUCE_APPLIER;
  }

  public WrappedAttribute findAttribute(String key) {
    key = keyTranslation(key);
    return attributes.get(key);
  }

  private static final boolean KEY_WRAPPED;
  private static final Map<String, String> REMAP;

  static {
    KEY_WRAPPED = MinecraftVersions.VER1_16_0.atOrAbove();
    Map<String, String> remap = new HashMap<>();
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
    REMAP = ImmutableMap.copyOf(remap);
  }

  private String keyTranslation(String key) {
    return KEY_WRAPPED ? REMAP.getOrDefault(key, key) : key;
  }

  public void modifyBaseValue(String key, double baseValue) {
    key = keyTranslation(key);
    WrappedAttribute attribute = findAttribute(key);
    if (attribute != null) {
      attributes.put(key, WrappedAttribute.newBuilder(attribute).baseValue(baseValue).modifiers(Collections.emptyList()).build());
      List<WrappedAttributeModifier> modifiers = modifiersOf(attribute);
      attributeModifiers.remove(key);
      attributeModifiers.put(key, modifiers);
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