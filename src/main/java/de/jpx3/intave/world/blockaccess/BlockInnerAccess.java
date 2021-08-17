package de.jpx3.intave.world.blockaccess;

import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.reflect.access.ReflectiveBlockAccess;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.*;

public final class BlockInnerAccess {
  private final static boolean MODERN_MATERIAL_PROCESSING = MinecraftVersions.VER1_14_0.atOrAbove();
  private final static Set<Material> clickableMaterials = new HashSet<>();
  private final static Set<Material> legacyMaterials = new HashSet<>();

  public static void setup() {
    loadMaterials();
  }

  @Deprecated
  public static boolean isLegacy(Material type) {
    return !MODERN_MATERIAL_PROCESSING && legacyMaterials.contains(type);
  }

  public static boolean isClickable(Material type) {
    return clickableMaterials.contains(type);
  }

  public static float blockDamage(Player player, ItemStack itemInHand, BlockPosition blockPosition) {
    return BlockAccessProvider.blockAccessor().blockDamage(player, itemInHand, blockPosition);
  }

  public static boolean replacementPlace(World world, Player player, BlockPosition blockPosition) {
    return BlockAccessProvider.blockAccessor().replacementPlace(world, player, blockPosition);
  }

  private static void loadMaterials() {
    if (MODERN_MATERIAL_PROCESSING) {
      modernMaterialLoad();
    } else {
      legacyMaterialLoad();
    }
  }

  private static void modernMaterialLoad() {
    Method isInteractable, isLegacy;
    try {
      isInteractable = Material.class.getMethod("isInteractable");
      isLegacy = Material.class.getMethod("isLegacy");
    } catch (NoSuchMethodException exception) {
      throw new IntaveInternalException(exception);
    }
    for (Material material : Material.values()) {
      try {
        boolean legacyInvoke = (boolean) isLegacy.invoke(material);
        if (legacyInvoke) {
          legacyMaterials.add(material);
        }
      } catch (Exception exception) {
        exception.printStackTrace();
      }
      try {
        if (material.isBlock() && (boolean) isInteractable.invoke(material)) {
          clickableMaterials.add(material);
        }
      } catch (Exception exception) {
        exception.printStackTrace();
      }
    }
  }

  private static void legacyMaterialLoad() {
    for (Material material : Material.values()) {
      if (!material.isBlock()) {
        continue;
      }
      Object block = ReflectiveBlockAccess.blockById(material.getId());
      if (block == null) {
        IntaveLogger.logger().pushPrintln("No block found for id " + material.getId());
        continue;
      }
      List<Method> methods = allMethodsIn(block.getClass());
      for (Method method : methods) {
        String methodName = method.getName();
        if (methodName.equalsIgnoreCase("interact")) {
          String declaringClassName = method.getDeclaringClass().getSimpleName();
          if (!declaringClassName.equals("Block") && !declaringClassName.equals("BlockBase")) {
            clickableMaterials.add(material);
          }
        }
      }
    }
  }

  private static List<Method> allMethodsIn(Class<?> clazz) {
    List<Method> methods = new ArrayList<>();
    do {
      Class<?> finalClazz = clazz;
      Arrays.stream(clazz.getDeclaredMethods())
        .filter(method -> method.getDeclaringClass() == finalClazz)
        .forEach(methods::add);
      clazz = clazz.getSuperclass();
    } while (clazz != Object.class);
    return methods;
  }
}
