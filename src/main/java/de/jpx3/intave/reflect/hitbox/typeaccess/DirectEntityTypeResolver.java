package de.jpx3.intave.reflect.hitbox.typeaccess;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.reflect.Lookup;
import de.jpx3.intave.reflect.access.ReflectiveAccess;
import de.jpx3.intave.reflect.hitbox.HitBoxBoundaries;
import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import net.minecraft.server.v1_16_R1.EntitySize;
import net.minecraft.server.v1_16_R1.EntityTypes;
import net.minecraft.server.v1_16_R1.IChatBaseComponent;
import net.minecraft.server.v1_16_R1.IRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@PatchyAutoTranslation
public final class DirectEntityTypeResolver {
  private static Field entitySizeField;
  private static Method componentExtractionMethod;

  static void setup() {
    try {
      Class<?> entityTypesClass = Lookup.serverClass("EntityTypes");
      Class<?> entitySizeClass = Lookup.serverClass("EntitySize");
      for (Field field : entityTypesClass.getDeclaredFields()) {
        if (field.getType() == entitySizeClass) {
          entitySizeField = field;
          break;
        }
      }
      String methodName = "g";
      if (MinecraftVersions.VER1_17_0.atOrAbove()) {
        methodName = "h";
      }
      componentExtractionMethod = Lookup.serverClass("EntityTypes").getMethod(methodName);
      if (entitySizeField == null) {
        throw new IntaveInternalException("EntitySize field does not exist in " + entityTypesClass);
      }
      ReflectiveAccess.ensureAccessible(entitySizeField);
    } catch (Exception exception) {
      throw new IntaveInternalException(exception);
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
    IChatBaseComponent component;
    try {
      component = (IChatBaseComponent) componentExtractionMethod.invoke(entityTypes);
    } catch (IllegalAccessException | InvocationTargetException exception) {
      throw new IntaveInternalException(exception);
    }
    return component.getString();
  }
}