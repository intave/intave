package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes.Property;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes.PropertyModifier;
import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.player.attribute.AttributeModifier;
import de.jpx3.intave.share.MinecraftKey;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.UPDATE_ATTRIBUTES;

public final class AttributeTracker extends Module {

  /**
   * Attribute key spellings, PacketEvents to ProtocolLib.
   * <p>
   * ProtocolLib reports the attribute name the running server uses, which is the spelling
   * {@code AbilityMetadata#keyTranslation} and {@code AbilityMetadata#setupAttributes} are written
   * against: the classic attributes keep their pre 1.16 camel case names there and every attribute
   * added from 1.16 on is spelled in snake case. PacketEvents instead resolves every attribute
   * against its own registry while decoding and hands out the canonical registry name, which is
   * always the namespaced snake case form ({@code minecraft:generic.movement_speed}) no matter
   * which protocol the packet arrived on.
   * <p>
   * This table is the exact inverse of {@code AbilityMetadata}'s legacy remap, so a PacketEvents
   * name is handed to {@code hasAttribute} in the same spelling ProtocolLib would have produced and
   * the existing per-version translation there resolves it identically on 1.8, on 1.16 and on
   * 1.21.2+. Every other attribute Intave registers is already spelled the same on both engines and
   * passes through unchanged; an attribute that is in neither table is dropped by
   * {@code hasAttribute}, which is what the ProtocolLib path does with it too.
   */
  private static final Map<String, String> PROTOCOL_LIB_KEY_SPELLING;

  static {
    Map<String, String> spelling = new HashMap<>();
    spelling.put("generic.max_health", "generic.maxHealth");
    spelling.put("generic.follow_range", "generic.followRange");
    spelling.put("generic.knockback_resistance", "generic.knockbackResistance");
    spelling.put("generic.movement_speed", "generic.movementSpeed");
    spelling.put("generic.attack_damage", "generic.attackDamage");
    spelling.put("generic.attack_speed", "generic.attackSpeed");
    spelling.put("generic.armor_toughness", "generic.armorToughness");
    spelling.put("generic.attack_knockback", "generic.attackKnockback");
    spelling.put("horse.jump_strength", "horse.jumpStrength");
    spelling.put("zombie.spawn_reinforcements", "zombie.spawnReinforcements");
    PROTOCOL_LIB_KEY_SPELLING = ImmutableMap.copyOf(spelling);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      UPDATE_ATTRIBUTES
    }
  )
  public void sentAttributes(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    if (packet.getIntegers().read(0) == player.getEntityId()) {
      StructureModifier<List<WrappedAttribute>> mod = packet.getAttributeCollectionModifier();
      List<WrappedAttribute> attributes = mod.read(0);
      mod.write(0, attributes);
      user.tickFeedback(() -> {
        attributes.forEach(attribute -> receivedAttribute(user, attribute));
      });
    }
  }

  /**
   * PacketEvents entry point for the same packet.
   * <p>
   * The packet is decoded here rather than in the tick feedback callback, because a PacketEvents
   * wrapper reads the connection's packet buffer and that buffer is gone by the time the callback
   * runs. The conversion itself is the same one {@code Attribute#fromProtocolLib} performs - base
   * value, modifier uuid, modifier key, operation and amount are the very wire fields ProtocolLib
   * reads - with two documented differences:
   * <ul>
   *   <li>the attribute name is translated back to the ProtocolLib spelling through
   *       {@link #PROTOCOL_LIB_KEY_SPELLING}, so the version dependent lookup in
   *       {@code AbilityMetadata} resolves it exactly as it does on the ProtocolLib path;</li>
   *   <li>the modifier's display name, which ProtocolLib takes from the NMS modifier, has no
   *       PacketEvents counterpart - it is not a wire field on any version - so the modifier's
   *       registry path is used instead. Nothing reads that field: it only takes part in
   *       {@code AttributeModifier}'s equals, hashCode and toString, and within one packet the
   *       modifiers are already distinguished by their uuid and key.</li>
   * </ul>
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      UPDATE_ATTRIBUTES
    }
  )
  public void sentAttributes(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    if (event.getPacketType() != PacketType.Play.Server.UPDATE_ATTRIBUTES) {
      return;
    }
    List<Attribute> attributes = readOwnAttributes(event, player.getEntityId());
    if (attributes == null) {
      return;
    }
    User user = UserRepository.userOf(player);
    user.tickFeedback(() -> {
      attributes.forEach(attribute ->
        applyAttribute(user, attribute.attributeKey(), () -> attribute)
      );
    });
  }

  /**
   * @return the packet's attributes converted to Intave's engine neutral type, or null when the
   * packet describes another entity or cannot be decoded.
   */
  private static @Nullable List<Attribute> readOwnAttributes(PacketSendEvent event, int ownEntityId) {
    try {
      WrapperPlayServerUpdateAttributes wrapper = new WrapperPlayServerUpdateAttributes(event);
      if (wrapper.getEntityId() != ownEntityId) {
        return null;
      }
      List<Property> properties = wrapper.getProperties();
      if (properties == null) {
        return new ArrayList<>();
      }
      List<Attribute> attributes = new ArrayList<>(properties.size());
      for (Property property : properties) {
        Attribute attribute = convertProperty(property);
        if (attribute != null) {
          attributes.add(attribute);
        }
      }
      return attributes;
    } catch (RuntimeException undecodable) {
      // PacketEvents resolves the attribute name against its own registry while decoding and
      // throws when it does not know it - a modded attribute, or one newer than its mappings.
      // ProtocolLib hands such a name out as a plain string, which hasAttribute() then drops, so
      // ignoring the packet here reaches the same end state without throwing out of the listener.
      return null;
    }
  }

  /** @return the converted attribute, or null when the packet carries no name to key it by. */
  private static @Nullable Attribute convertProperty(@Nullable Property property) {
    if (property == null) {
      return null;
    }
    String attributeKey = attributeKeyOf(property);
    if (attributeKey == null) {
      return null;
    }
    Set<AttributeModifier> modifiers = new HashSet<>();
    List<PropertyModifier> propertyModifiers = property.getModifiers();
    if (propertyModifiers != null) {
      for (PropertyModifier propertyModifier : propertyModifiers) {
        if (propertyModifier != null) {
          modifiers.add(convertModifier(propertyModifier));
        }
      }
    }
    // fromProtocolLib seeds the default base value with the packet's base value; mirrored here.
    double baseValue = property.getValue();
    return Attribute.newBuilder()
      .withAttributeKey(attributeKey)
      .withDefaultBaseValue(baseValue)
      .withBaseValue(baseValue)
      .withAttributeModifiers(modifiers)
      .build();
  }

  private static AttributeModifier convertModifier(PropertyModifier modifier) {
    ResourceLocation name = modifier.getName();
    return new AttributeModifier(
      name == null ? null : new MinecraftKey(name.getNamespace(), name.getKey()),
      modifier.getUUID(),
      name == null ? null : name.getKey(),
      AttributeModifier.Operation.fromId(modifier.getOperation().ordinal()),
      modifier.getAmount()
    );
  }

  /** @return the attribute's name in the spelling ProtocolLib reports, or null when it has none. */
  private static @Nullable String attributeKeyOf(Property property) {
    com.github.retrooper.packetevents.protocol.attribute.Attribute attribute = property.getAttribute();
    ResourceLocation name = attribute == null ? null : attribute.getName();
    if (name == null) {
      return null;
    }
    String key = "minecraft".equals(name.getNamespace()) ? name.getKey() : name.toString();
    return PROTOCOL_LIB_KEY_SPELLING.getOrDefault(key, key);
  }

  private void receivedAttribute(User user, WrappedAttribute attribute) {
    // The conversion stays behind the hasAttribute() gate, exactly where it was.
    applyAttribute(user, attribute.getAttributeKey(), () -> Attribute.fromProtocolLib(attribute));
  }

  /**
   * Engine independent attribute handling. The key is passed separately from the attribute so the
   * ProtocolLib path can keep converting lazily, only once the attribute is known to be tracked.
   */
  private void applyAttribute(User user, String attributeKey, Supplier<Attribute> attributeSupplier) {
    AbilityMetadata abilities = user.meta().abilities();
    MovementMetadata movement = user.meta().movement();
    if (abilities.hasAttribute(attributeKey)) {
      Attribute intaveAttribute = attributeSupplier.get();
      List<AttributeModifier> intaveAttributes = abilities.modifiersOf(intaveAttribute);
      intaveAttributes.clear();
      Set<AttributeModifier> serverAttributes = intaveAttribute.modifiers();
      if (abilities.findAttribute(attributeKey) == abilities.findAttribute("generic.movementSpeed")) {
        movement.hasSprintSpeed = AbilityMetadata.hasSprintModifier(serverAttributes);
      }
      intaveAttributes.addAll(new HashSet<>(serverAttributes));
      abilities.modifyBaseValue(attributeKey, intaveAttribute.baseValue());
    }
  }
}
