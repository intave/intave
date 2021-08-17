package de.jpx3.intave.user.meta;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import com.comphenix.protocol.wrappers.WrappedAttributeModifier;
import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.event.dispatch.PlayerAbilityTracker;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

@Relocate
public final class AbilityMetadata {
  private static final UUID SPEED_MODIFIER_SPRINTING_UUID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
  public final static Predicate<WrappedAttributeModifier> EXCLUDE_SPRINT_MODIFIER = modifier -> !modifier.getUUID().equals(SPEED_MODIFIER_SPRINTING_UUID);

  private final Player player;
  private boolean flying;
  private boolean allowFlying;

  private PlayerAbilityTracker.GameMode gameMode;
  private PlayerAbilityTracker.GameMode pendingGameMode;

  private float flySpeed = 0.05f;
  private float walkSpeed = 0.1f;

  private final Map<WrappedAttribute, List<WrappedAttributeModifier>> attributeModifiers = new ConcurrentHashMap<>();

  public float unsynchronizedHealth;
  public float health;
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
      throw new IntaveInternalException("Player gameMode reference is null?");
    }
    int gameModeValue = gameMode.getValue();
    this.gameMode = Arrays.stream(PlayerAbilityTracker.GameMode.values())
      .filter(mode -> mode.id() == gameModeValue)
      .findFirst()
      .orElse(PlayerAbilityTracker.GameMode.NOT_SET);
    this.pendingGameMode = this.gameMode;
  }

  public void setupAttributes() {
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.UPDATE_ATTRIBUTES);
    boolean atLeastMinecraft16 = MinecraftVersions.VER1_16_0.atOrAbove();
    attributeModifiers.put(WrappedAttribute.newBuilder().attributeKey(keyTranslation("generic.movementSpeed")).baseValue(atLeastMinecraft16 ? (double) 0.1F : 0.1).packet(packet).build(), new CopyOnWriteArrayList<>());
  }

  public double attributeValue(String key) {
    return attributeValue(key, x -> true);
  }

  public double attributeValue(String key, Predicate<WrappedAttributeModifier> filter) {
    key = keyTranslation(key);
    for (Map.Entry<WrappedAttribute, List<WrappedAttributeModifier>> wrappedAttributeListEntry : attributeModifiers.entrySet()) {
      WrappedAttribute attribute = wrappedAttributeListEntry.getKey();
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
    attribute = attribute.withModifiers(Collections.emptyList());
    return attributeModifiers.computeIfAbsent(attribute, x -> new CopyOnWriteArrayList<>());
  }

  public WrappedAttribute findAttribute(String key) {
    key = keyTranslation(key);
    for (WrappedAttribute wrappedAttribute : attributeModifiers.keySet()) {
      if (wrappedAttribute.getAttributeKey().equals(key)) {
        return wrappedAttribute;
      }
    }
    return null;
  }

  private final static boolean KEY_WRAPPED;
  private final static Map<String, String> REMAP;

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
    WrappedAttribute attribute = findAttribute(key);
    if (attribute != null) {
      List<WrappedAttributeModifier> modifiers = modifiersOf(attribute);
      attributeModifiers.remove(attribute);
      attribute = WrappedAttribute.newBuilder(attribute).baseValue(baseValue).build();
      attributeModifiers.put(attribute, modifiers);
    }
  }

  public boolean inGameModeIncludePending(PlayerAbilityTracker.GameMode gameMode) {
    return this.gameMode == gameMode || this.pendingGameMode == gameMode;
  }

  public boolean ignoringMovementPackets() {
    return inGameModeIncludePending(PlayerAbilityTracker.GameMode.SPECTATOR) || hasViewEntity;
  }

  public boolean inGameMode(GameMode gameMode) {
    return this.gameMode.id() == gameMode.getValue();
  }

  public boolean inGameMode(PlayerAbilityTracker.GameMode gameMode) {
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
    modifyBaseValue("generic.movementSpeed", walkSpeed);
  }

  public void setFlySpeed(float flySpeed) {
    this.flySpeed = flySpeed;
  }

  public void setGameMode(PlayerAbilityTracker.GameMode gameMode) {
    if (this.gameMode == PlayerAbilityTracker.GameMode.SPECTATOR && gameMode == PlayerAbilityTracker.GameMode.CREATIVE) {
      setAllowFlying(true);
      setFlying(true);
    }
    this.gameMode = gameMode;
  }

  public void setPendingGameMode(PlayerAbilityTracker.GameMode pendingGameMode) {
    this.pendingGameMode = pendingGameMode;
  }
}