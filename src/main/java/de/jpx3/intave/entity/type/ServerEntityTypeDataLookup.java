package de.jpx3.intave.entity.type;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import de.jpx3.intave.entity.size.HitboxSize;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class ServerEntityTypeDataLookup implements EntityTypeDataResolver {
  private static final Map<String, HitboxSize> DIMENSIONS = new HashMap<>();

  static {
    register("item", 0.25F, 0.25F);
    register("experience_orb", 0.5F, 0.5F);
    register("area_effect_cloud", 6.0F, 0.5F);
    register("leash_knot", 0.5F, 0.5F);
    register("painting", 0.5F, 0.5F);
    register("item_frame", 0.5F, 0.5F);
    register("glow_item_frame", 0.5F, 0.5F);
    register("armor_stand", 0.5F, 1.975F);
    register("marker", 0.0F, 0.0F);

    register("arrow", 0.5F, 0.5F);
    register("spectral_arrow", 0.5F, 0.5F);
    register("trident", 0.5F, 0.5F);
    register("snowball", 0.25F, 0.25F);
    register("egg", 0.25F, 0.25F);
    register("ender_pearl", 0.25F, 0.25F);
    register("eye_of_ender", 0.25F, 0.25F);
    register("experience_bottle", 0.25F, 0.25F);
    register("potion", 0.25F, 0.25F);
    register("splash_potion", 0.25F, 0.25F);
    register("lingering_potion", 0.25F, 0.25F);
    register("firework_rocket", 0.25F, 0.25F);
    register("fireball", 3.0F, 3.0F);
    register("dragon_fireball", 1.0F, 1.0F);
    register("small_fireball", 1.0F, 1.0F);
    register("wither_skull", 0.3125F, 0.3125F);
    register("wind_charge", 0.3125F, 0.3125F);
    register("breeze_wind_charge", 0.3125F, 0.3125F);

    register("tnt", 0.98F, 0.98F);
    register("falling_block", 0.98F, 0.98F);
    register("end_crystal", 2.0F, 2.0F);
    register("fishing_bobber", 0.25F, 0.25F);
    register("interaction", 1.0F, 1.0F);
    register("block_display", 0.0F, 0.0F);
    register("item_display", 0.0F, 0.0F);
    register("text_display", 0.0F, 0.0F);

    register("boat", 1.5F, 0.6F);
    register("chest_boat", 1.5F, 0.6F);
    register("raft", 1.5F, 0.6F);
    register("chest_raft", 1.5F, 0.6F);
    register("minecart", 0.98F, 0.7F);
    register("chest_minecart", 0.98F, 0.7F);
    register("command_block_minecart", 0.98F, 0.7F);
    register("furnace_minecart", 0.98F, 0.7F);
    register("hopper_minecart", 0.98F, 0.7F);
    register("spawner_minecart", 0.98F, 0.7F);
    register("tnt_minecart", 0.98F, 0.7F);

    register("player", 0.6F, 1.8F);
    register("allay", 0.35F, 0.6F);
    register("armadillo", 0.7F, 0.65F);
    register("axolotl", 0.75F, 0.42F);
    register("bat", 0.5F, 0.9F);
    register("bee", 0.7F, 0.6F);
    register("blaze", 0.6F, 1.8F);
    register("camel", 1.7F, 2.375F);
    register("cat", 0.6F, 0.7F);
    register("cave_spider", 0.7F, 0.5F);
    register("chicken", 0.4F, 0.7F);
    register("cod", 0.5F, 0.3F);
    register("cow", 0.9F, 1.4F);
    register("creeper", 0.6F, 1.7F);
    register("dolphin", 0.9F, 0.6F);
    register("donkey", 1.3965F, 1.5F);
    register("drowned", 0.6F, 1.95F);
    register("elder_guardian", 1.9975F, 1.9975F);
    register("ender_dragon", 16.0F, 8.0F);
    register("enderman", 0.6F, 2.9F);
    register("endermite", 0.4F, 0.3F);
    register("evoker", 0.6F, 1.95F);
    register("fox", 0.6F, 0.7F);
    register("frog", 0.5F, 0.5F);
    register("ghast", 4.0F, 4.0F);
    register("giant", 3.6F, 11.7F);
    register("glow_squid", 0.8F, 0.8F);
    register("goat", 0.9F, 1.3F);
    register("guardian", 0.85F, 0.85F);
    register("hoglin", 1.3965F, 1.4F);
    register("horse", 1.3965F, 1.6F);
    register("husk", 0.6F, 1.95F);
    register("illusioner", 0.6F, 1.95F);
    register("iron_golem", 1.4F, 2.7F);
    register("llama", 0.9F, 1.87F);
    register("magma_cube", 0.51000005F, 0.51000005F);
    register("mooshroom", 0.9F, 1.4F);
    register("mule", 1.3965F, 1.6F);
    register("ocelot", 0.6F, 0.7F);
    register("panda", 1.3F, 1.25F);
    register("parrot", 0.5F, 0.9F);
    register("phantom", 0.9F, 0.5F);
    register("pig", 0.9F, 0.9F);
    register("piglin", 0.6F, 1.95F);
    register("piglin_brute", 0.6F, 1.95F);
    register("pillager", 0.6F, 1.95F);
    register("polar_bear", 1.4F, 1.4F);
    register("pufferfish", 0.7F, 0.7F);
    register("rabbit", 0.4F, 0.5F);
    register("ravager", 1.95F, 2.2F);
    register("salmon", 0.7F, 0.4F);
    register("sheep", 0.9F, 1.3F);
    register("shulker", 1.0F, 1.0F);
    register("silverfish", 0.4F, 0.3F);
    register("skeleton", 0.6F, 1.99F);
    register("skeleton_horse", 1.3965F, 1.6F);
    register("slime", 0.51000005F, 0.51000005F);
    register("sniffer", 1.9F, 1.75F);
    register("snow_golem", 0.7F, 1.9F);
    register("spider", 1.4F, 0.9F);
    register("squid", 0.8F, 0.8F);
    register("stray", 0.6F, 1.99F);
    register("strider", 0.9F, 1.7F);
    register("tadpole", 0.4F, 0.3F);
    register("trader_llama", 0.9F, 1.87F);
    register("tropical_fish", 0.5F, 0.4F);
    register("turtle", 1.2F, 0.4F);
    register("vex", 0.4F, 0.8F);
    register("villager", 0.6F, 1.95F);
    register("vindicator", 0.6F, 1.95F);
    register("wandering_trader", 0.6F, 1.95F);
    register("warden", 0.9F, 2.9F);
    register("witch", 0.6F, 1.95F);
    register("wither", 0.9F, 3.5F);
    register("wither_skeleton", 0.7F, 2.4F);
    register("wolf", 0.6F, 0.85F);
    register("zoglin", 1.3965F, 1.4F);
    register("zombie", 0.6F, 1.95F);
    register("zombie_horse", 1.3965F, 1.6F);
    register("zombie_villager", 0.6F, 1.95F);
    register("zombified_piglin", 0.6F, 1.95F);
  }

  @Override
  public EntityTypeData resolveFor(int entityTypeId, boolean isLivingEntity) {
    EntityType entityType = EntityTypes.getById(serverVersion(), entityTypeId);
    if (entityType == null) {
      return null;
    }
    String key = entityType.getName().getKey();
    boolean living = isLivingEntity || entityType.isInstanceOf(EntityTypes.LIVINGENTITY);
    return new EntityTypeData(displayName(key), dimensionsByKey(key), entityTypeId, living, 11);
  }

  static HitboxSize dimensionsByKey(String key) {
    HitboxSize hitboxSize = DIMENSIONS.get(normalizeKey(key));
    return hitboxSize == null ? HitboxSize.zero() : hitboxSize;
  }

  private static ClientVersion serverVersion() {
    return PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
  }

  private static void register(String key, float width, float height) {
    DIMENSIONS.put(normalizeKey(key), HitboxSize.of(width, height));
  }

  private static String displayName(String key) {
    String normalized = normalizeKey(key);
    StringBuilder builder = new StringBuilder();
    boolean capitalize = true;
    for (int i = 0; i < normalized.length(); i++) {
      char character = normalized.charAt(i);
      if (character == '_') {
        capitalize = true;
      } else if (capitalize) {
        builder.append(Character.toUpperCase(character));
        capitalize = false;
      } else {
        builder.append(character);
      }
    }
    return builder.length() == 0 ? "Unknown" : builder.toString();
  }

  private static String normalizeKey(String key) {
    String normalized = key.toLowerCase(Locale.ROOT);
    int namespaceEnd = normalized.indexOf(':');
    if (namespaceEnd >= 0) {
      normalized = normalized.substring(namespaceEnd + 1);
    }
    if (normalized.endsWith("_chest_boat")) {
      return "chest_boat";
    }
    if (normalized.endsWith("_boat")) {
      return "boat";
    }
    if (normalized.endsWith("_chest_raft")) {
      return "chest_raft";
    }
    if (normalized.endsWith("_raft")) {
      return "raft";
    }
    return normalized;
  }
}
