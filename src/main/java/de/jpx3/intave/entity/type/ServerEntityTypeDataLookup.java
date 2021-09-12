package de.jpx3.intave.entity.type;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.clazz.Lookup;
import de.jpx3.intave.clazz.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.entity.size.HitboxSize;
import net.minecraft.server.v1_16_R1.EntitySize;
import net.minecraft.server.v1_16_R1.EntityTypes;
import net.minecraft.server.v1_16_R1.IChatBaseComponent;
import net.minecraft.server.v1_16_R1.IRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@PatchyAutoTranslation
final class ServerEntityTypeDataLookup implements EntityTypeDataResolver {
  private static Field entitySizeField;
  private static final Method componentExtractionMethod;

  static {
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
      ensureAccessibility(entitySizeField);
    } catch (Exception exception) {
      throw new IntaveInternalException(exception);
    }
  }

  private static Field ensureAccessibility(Field field) {
    if (!field.isAccessible()) {
      field.setAccessible(true);
    }
    return field;
  }

  @Override
  public EntityTypeData resolveFor(int entityType, boolean isLivingEntity) {
    String entityName = nameOf(entityType);
    HitboxSize hitBoxSize = dimensionsOf(entityType);
    return new EntityTypeData(entityName, hitBoxSize, entityType, isLivingEntity, 11);
  }

  @PatchyAutoTranslation
  public String nameOf(int type) {
    EntityTypes<?> entityTypes = IRegistry.ENTITY_TYPE.fromId(type);
    IChatBaseComponent component;
    try {
      component = (IChatBaseComponent) componentExtractionMethod.invoke(entityTypes);
    } catch (IllegalAccessException | InvocationTargetException exception) {
      throw new IntaveInternalException(exception);
    }
    return component.getString();
  }

  @PatchyAutoTranslation
  public HitboxSize dimensionsOf(int type) {
    try {
      EntityTypes<?> entityTypes = IRegistry.ENTITY_TYPE.fromId(type);
      EntitySize entitySize = (EntitySize) entitySizeField.get(entityTypes);
      return HitboxSize.of(entitySize.width, entitySize.height);
    } catch (IllegalAccessException e) {
      throw new IntaveInternalException(e);
    }
  }
}