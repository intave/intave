package de.jpx3.intave.reflect.hitbox.typeaccess;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.reflect.hitbox.HitBoxBoundaries;
import net.minecraft.server.v1_14_R1.EntitySize;
import net.minecraft.server.v1_14_R1.EntityTypes;
import net.minecraft.server.v1_14_R1.IChatBaseComponent;
import net.minecraft.server.v1_14_R1.IRegistry;

import java.lang.reflect.Field;

@PatchyAutoTranslation
final class EntityTypeResolverNew {
  private final static boolean ENTITY_SIZE = MinecraftVersions.VER1_14_0.atOrAbove();
  private static Field entitySizeField;

  static {
    if (ENTITY_SIZE) {
      lookupField();
    }
  }

  private static void lookupField() {
    try {
      Class<?> entityTypesClass = ReflectiveAccess.lookupServerClass("EntityTypes");
      Class<?> entitySizeClass = ReflectiveAccess.lookupServerClass("EntitySize");
      for (Field field : entityTypesClass.getDeclaredFields()) {
        if (field.getType() == entitySizeClass) {
          entitySizeField = field;
          break;
        }
      }
      if (entitySizeField == null) {
        throw new IntaveInternalException("EntitySize field does not exist in " + entityTypesClass);
      }
      ReflectiveAccess.ensureAccessible(entitySizeField);
    } catch (Exception e) {
      throw new IntaveInternalException(e);
    }
  }

  @PatchyAutoTranslation
  public static HitBoxBoundaries resolveBoundariesOf(int type) {
    try {
      EntityTypes<?> entityTypes = IRegistry.ENTITY_TYPE.fromId(type);
      EntitySize entitySize = (EntitySize) entitySizeField.get(entityTypes);
      return HitBoxBoundaries.of(entitySize.width, entitySize.height);
    } catch (IllegalAccessException e) {
      throw new IntaveInternalException(e);
    }
  }

  @PatchyAutoTranslation
  public static String resolveNameOf(int type) {
    EntityTypes<?> entityTypes = IRegistry.ENTITY_TYPE.fromId(type);
    IChatBaseComponent component = entityTypes.g();
    return component.getString();
  }
}