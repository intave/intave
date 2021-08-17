package de.jpx3.intave.reflect.hitbox.typeaccess;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.reflect.hitbox.HitBoxBoundaries;
import de.jpx3.intave.reflect.patchy.PatchyLoadingInjector;

import java.util.HashMap;
import java.util.Map;

public final class DualEntityTypeAccess {
  private final static boolean DIRECT_RESOLVE = MinecraftVersions.VER1_14_0.atOrAbove();
  private final static Map<Integer, EntityTypeData> entityTypeMap = new HashMap<>();

  public static void setup() {
    if (DIRECT_RESOLVE) {
      PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), "de.jpx3.intave.reflect.hitbox.typeaccess.DirectEntityTypeResolver");
      DirectEntityTypeResolver.setup();
    } else {
      loadEntityTypeData();
    }
  }

  private static void loadEntityTypeData() {
    entityTypeMap.put(1, new EntityTypeData("Item", HitBoxBoundaries.of(0.25F, 0.25F), 1, false));
    entityTypeMap.put(2, new EntityTypeData("XPOrb", HitBoxBoundaries.of(0.5F, 0.5F), 2, false));
    entityTypeMap.put(8, new EntityTypeData("LeashKnot", HitBoxBoundaries.of(0.5F, 0.5F), 8, false));
    entityTypeMap.put(9, new EntityTypeData("Painting", HitBoxBoundaries.of(0.5F, 0.5F), 9, false));
    entityTypeMap.put(10, new EntityTypeData("Arrow", HitBoxBoundaries.of(0.5F, 0.5F), 10, false));
    entityTypeMap.put(11, new EntityTypeData("Snowball", HitBoxBoundaries.of(0.25F, 0.25F), 11, false));
    entityTypeMap.put(12, new EntityTypeData("Fireball", HitBoxBoundaries.of(3.0F, 3.0F), 12, false));
    entityTypeMap.put(13, new EntityTypeData("SmallFireball", HitBoxBoundaries.of(1.0F, 1.0F), 13, false));
    entityTypeMap.put(14, new EntityTypeData("ThrownEnderpearl", HitBoxBoundaries.of(0.25F, 0.25F), 14, false));
    entityTypeMap.put(15, new EntityTypeData("EyeOfEnderSignal", HitBoxBoundaries.of(0.25F, 0.25F), 15, false));
    entityTypeMap.put(16, new EntityTypeData("ThrownPotion", HitBoxBoundaries.of(0.25F, 0.25F), 16, false));
    entityTypeMap.put(17, new EntityTypeData("ThrownExpBottle", HitBoxBoundaries.of(0.25F, 0.25F), 17, false));
    entityTypeMap.put(18, new EntityTypeData("ItemFrame", HitBoxBoundaries.of(0.5F, 0.5F), 18, false));
    entityTypeMap.put(19, new EntityTypeData("WitherSkull", HitBoxBoundaries.of(0.3125F, 0.3125F), 19, false));
    entityTypeMap.put(20, new EntityTypeData("PrimedTnt", HitBoxBoundaries.of(0.98F, 0.98F), 20, false));
    entityTypeMap.put(21, new EntityTypeData("FallingSand", HitBoxBoundaries.of(0.98F, 0.98F), 21, false));
    entityTypeMap.put(22, new EntityTypeData("FireworksRocketEntity", HitBoxBoundaries.of(0.25F, 0.25F), 22, false));
    entityTypeMap.put(30, new EntityTypeData("ArmorStand", HitBoxBoundaries.of(0.5F, 1.975F), 30, false));
    entityTypeMap.put(41, new EntityTypeData("Boat", HitBoxBoundaries.of(1.5F, 0.6F), 41, false));
    entityTypeMap.put(42, new EntityTypeData("Minecart", HitBoxBoundaries.of(0.98F, 0.7F), 42, false));
    entityTypeMap.put(43, new EntityTypeData("MinecartChest", HitBoxBoundaries.of(0.98F, 0.7F), 43, false));
    entityTypeMap.put(44, new EntityTypeData("MinecartFurnace", HitBoxBoundaries.of(0.98F, 0.7F), 44, false));
    entityTypeMap.put(45, new EntityTypeData("MinecartTNT", HitBoxBoundaries.of(0.98F, 0.7F), 45, false));
    entityTypeMap.put(46, new EntityTypeData("MinecartHopper", HitBoxBoundaries.of(0.98F, 0.7F), 46, false));
    entityTypeMap.put(47, new EntityTypeData("MinecartMobSpawner", HitBoxBoundaries.of(0.98F, 0.7F), 47, false));
    entityTypeMap.put(40, new EntityTypeData("MinecartCommandBlock", HitBoxBoundaries.of(0.98F, 0.7F), 40, false));
    entityTypeMap.put(48, new EntityTypeData("Mob", HitBoxBoundaries.of(0, 0), 48, true));
    entityTypeMap.put(49, new EntityTypeData("Monster", HitBoxBoundaries.of(0, 0), 49, true));
    entityTypeMap.put(50, new EntityTypeData("Creeper", HitBoxBoundaries.of(0.6F, 1.95F), 50, true));
    entityTypeMap.put(51, new EntityTypeData("Skeleton", HitBoxBoundaries.of(0.6F, 1.95F), 51, true));
    entityTypeMap.put(52, new EntityTypeData("Spider", HitBoxBoundaries.of(1.4F, 0.9F), 52, true));
    entityTypeMap.put(53, new EntityTypeData("Giant", HitBoxBoundaries.of(0.6f * 6f, 1.95f * 6f), 53, true));
    entityTypeMap.put(54, new EntityTypeData("Zombie", HitBoxBoundaries.of(0.6F, 1.95F), 54, true));
    entityTypeMap.put(55, new EntityTypeData("Slime", HitBoxBoundaries.of(0.51000005F * 1f, 0.51000005F * 1f), 55, true));
    entityTypeMap.put(56, new EntityTypeData("Ghast", HitBoxBoundaries.of(4.0F, 4.0F), 56, true));
    entityTypeMap.put(57, new EntityTypeData("PigZombie", HitBoxBoundaries.of(0.6F, 1.95F), 57, true));
    entityTypeMap.put(58, new EntityTypeData("Enderman", HitBoxBoundaries.of(0.6F, 2.9F), 58, true));
    entityTypeMap.put(59, new EntityTypeData("CaveSpider", HitBoxBoundaries.of(0.7F, 0.5F), 59, true));
    entityTypeMap.put(60, new EntityTypeData("Silverfish", HitBoxBoundaries.of(0.4F, 0.3F), 60, true));
    entityTypeMap.put(61, new EntityTypeData("Blaze", HitBoxBoundaries.of(0.6F, 1.95F), 61, true));
    entityTypeMap.put(62, new EntityTypeData("LavaSlime", HitBoxBoundaries.of(0.51000005F * 1f, 0.51000005F * 1f), 62, true));
    entityTypeMap.put(63, new EntityTypeData("EnderDragon", HitBoxBoundaries.of(16.0F, 8.0F), 63, true));
    entityTypeMap.put(64, new EntityTypeData("WitherBoss", HitBoxBoundaries.of(0.9F, 3.5F), 64, true));
    entityTypeMap.put(65, new EntityTypeData("Bat", HitBoxBoundaries.of(0.5F, 0.9F), 65, true));
    entityTypeMap.put(66, new EntityTypeData("Witch", HitBoxBoundaries.of(0.6F, 1.95F), 66, true));
    entityTypeMap.put(67, new EntityTypeData("Endermite", HitBoxBoundaries.of(0.4F, 0.3F), 67, true));
    entityTypeMap.put(68, new EntityTypeData("Guardian", HitBoxBoundaries.of(0.85F, 0.85F), 68, true));
    entityTypeMap.put(90, new EntityTypeData("Pig", HitBoxBoundaries.of(0.9F, 0.9F), 90, true));
    entityTypeMap.put(91, new EntityTypeData("Sheep", HitBoxBoundaries.of(0.9F, 1.3F), 91, true));
    entityTypeMap.put(92, new EntityTypeData("Cow", HitBoxBoundaries.of(0.9F, 1.3F), 92, true));
    entityTypeMap.put(93, new EntityTypeData("Chicken", HitBoxBoundaries.of(0.4F, 0.7F), 93, true));
    entityTypeMap.put(94, new EntityTypeData("Squid", HitBoxBoundaries.of(0.95F, 0.95F), 94, true));
    entityTypeMap.put(95, new EntityTypeData("Wolf", HitBoxBoundaries.of(0.6F, 0.8F), 95, true));
    entityTypeMap.put(96, new EntityTypeData("MushroomCow", HitBoxBoundaries.of(0.9F, 1.3F), 96, true));
    entityTypeMap.put(97, new EntityTypeData("SnowMan", HitBoxBoundaries.of(0.7F, 1.9F), 97, true));
    entityTypeMap.put(98, new EntityTypeData("Ozelot", HitBoxBoundaries.of(0.6F, 0.7F), 98, true));
    entityTypeMap.put(99, new EntityTypeData("VillagerGolem", HitBoxBoundaries.of(1.4F, 2.9F), 99, true));
    entityTypeMap.put(100, new EntityTypeData("Horse", HitBoxBoundaries.of(1.4F, 1.6F), 100, true));
    entityTypeMap.put(101, new EntityTypeData("Rabbit", HitBoxBoundaries.of(0.6F, 0.7F), 101, true));
    entityTypeMap.put(105, new EntityTypeData("Player", HitBoxBoundaries.of(0.6F, 1.8F), 105, true));
    entityTypeMap.put(120, new EntityTypeData("Villager", HitBoxBoundaries.of(0.6F, 1.8F), 120, true));
    entityTypeMap.put(200, new EntityTypeData("EnderCrystal", HitBoxBoundaries.of(2.0F, 2.0F), 200, false));
  }

  public static EntityTypeData resolveFromId(int entityTypeId, boolean isLivingEntity) {
    return DIRECT_RESOLVE ? newResolve(entityTypeId, isLivingEntity) : legacyResolve(entityTypeId);
  }

  private static EntityTypeData newResolve(int entityTypeId, boolean isLivingEntity) {
    String entityName = DirectEntityTypeResolver.resolveNameOf(entityTypeId);
    HitBoxBoundaries hitBoxBoundaries = DirectEntityTypeResolver.resolveBoundariesOf(entityTypeId);
    return new EntityTypeData(entityName, hitBoxBoundaries, entityTypeId, isLivingEntity);
  }

  private static EntityTypeData legacyResolve(int entityTypeId) {
    if (entityTypeId != -1) {
      return entityTypeMap.get(entityTypeId);
    } else {
      return null;
    }
  }
}