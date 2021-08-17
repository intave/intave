package de.jpx3.intave.reflect.access;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.reflect.Lookup;
import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.Packet;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

@PatchyAutoTranslation
public final class ReflectiveEntityAccess {
  public final static boolean REFLECTIVE_ACCESS = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_16_0);

  private final static Class<?> ENTITY_CLASS = ReflectiveAccess.NMS_ENTITY_CLASS;
  private final static Field ENTITY_GROUND_FIELD = Lookup.declaredFieldIn(ENTITY_CLASS, MinecraftVersions.VER1_17_0.atOrAbove() ? "z" :"onGround");

  static {
    ENTITY_GROUND_FIELD.setAccessible(true);
  }

  @PatchyAutoTranslation
  public static void setOnGround(Player player, boolean onGround) {
    Entity entity = ((CraftEntity) player).getHandle();
    if (REFLECTIVE_ACCESS) {
      fieldSetOnGround(entity, onGround);
    } else {
      entity.onGround = onGround;
    }
  }

  @PatchyAutoTranslation
  public static boolean onGround(Player player) {
    Entity entity = ((CraftEntity) player).getHandle();
    return REFLECTIVE_ACCESS ? fieldGetOnGround(entity) : entity.onGround;
  }

  private static void fieldSetOnGround(Object entity, boolean onGround) {
    try {
      ENTITY_GROUND_FIELD.set(entity, onGround);
    } catch (IllegalAccessException e) {
      throw new IntaveInternalException(e);
    }
  }

  private static boolean fieldGetOnGround(Object entity) {
    try {
      return (boolean) ENTITY_GROUND_FIELD.get(entity);
    } catch (IllegalAccessException e) {
      throw new IntaveInternalException(e);
    }
  }

  @PatchyAutoTranslation
  public static void addToSendQueue(Player player, Object packet) {
    ((CraftPlayer) player).getHandle().playerConnection.sendPacket((Packet<?>) packet);
  }
}