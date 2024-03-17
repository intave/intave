package de.jpx3.intave.block.access;

import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;

public final class BlockInteractionAccess {
  private static final boolean MODERN_MATERIAL_PROCESSING = MinecraftVersions.VER1_14_0.atOrAbove();
  private static final Set<Material> clickableMaterials = new HashSet<>();

  public static void setup() {
    loadMaterials();
  }

  public static boolean isClickable(Material type) {
    return clickableMaterials.contains(type);
  }

  public static float blockDamage(@NotNull Player player, @Nullable ItemStack itemInHand, @NotNull BlockPosition blockPosition) {
    if (player == null || blockPosition == null) {
      throw new IllegalArgumentException("Player and block position must not be null: " + player + ", " + blockPosition);
    }
    return BlockAccess.global().blockDamage(player.getWorld(), player, itemInHand, blockPosition);
  }

  public static boolean replacedOnPlacement(World world, Player player, BlockPosition blockPosition) {
    return BlockAccess.global().replacementPlace(world, player, blockPosition);
  }

  private static void loadMaterials() {
    if (MODERN_MATERIAL_PROCESSING) {
      modernMaterialLoad();
    } else {
      legacyMaterialLoad();
    }
  }

  private static void modernMaterialLoad() {
    Method isInteractable;
    try {
      isInteractable = Material.class.getMethod("isInteractable");
    } catch (NoSuchMethodException exception) {
      throw new IntaveInternalException(exception);
    }
    for (Material material : Material.values()) {
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
      Object block = BlockAccess.global().nativeVariantBy(material.getId());
      if (block == null) {
        IntaveLogger.logger().printLine("No block found for id " + material.getId());
        continue;
      }
      if (hasInteractMethod(block.getClass())) {
        clickableMaterials.add(material);
      }
    }
  }

  private static boolean hasInteractMethod(Class<?> blockClass) {
    for (Method method : allMethodsIn(blockClass)) {
      String methodName = method.getName();
      if (methodName.equalsIgnoreCase("interact")) {
        String declaringClassName = method.getDeclaringClass().getSimpleName();
        if (!declaringClassName.equals("Block") && !declaringClassName.equals("BlockBase")) {
          return true;
        }
      }
    }
    return false;
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
