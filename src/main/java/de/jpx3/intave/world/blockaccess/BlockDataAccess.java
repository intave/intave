package de.jpx3.intave.world.blockaccess;

import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.reflect.ReflectiveBlockAccess;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.*;

public final class BlockDataAccess {
  private static BlockAccessor blockAccessor;
  private static MethodHandle nativeBlockDataAccess;
  private static MethodHandle nativeBlockDataExtractionAccess;

  private final static boolean NEW_MATERIAL_PROCESSING = MinecraftVersions.VER1_14_0.atOrAbove();
  private final static boolean NEW_BLOCK_ACCESS = MinecraftVersions.VER1_13_0.atOrAbove();

  private final static Set<Material> clickableMaterials = new HashSet<>();
  private final static Set<Material> legacyMaterials = new HashSet<>();

  public static void setup() {
    String resolverName = "de.jpx3.intave.world.blockaccess.v8BlockAccessor";
    if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      resolverName = "de.jpx3.intave.world.blockaccess.v9BlockAccessor";
    }
    if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      resolverName = "de.jpx3.intave.world.blockaccess.v13BlockAccessor";
    }
    if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      resolverName = "de.jpx3.intave.world.blockaccess.v14BlockAccessor";
    }
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      resolverName = "de.jpx3.intave.world.blockaccess.v17BlockAccessor";
    }
    ClassLoader classLoader = IntavePlugin.class.getClassLoader();
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, resolverName);
    blockAccessor = instanceOf(resolverName);
    try {
      if (NEW_BLOCK_ACCESS) {
        Class<?> blockDataClass = ReflectiveAccess.lookupServerClass("IBlockData");
        Class<?> craftBukkitClass = ReflectiveAccess.lookupCraftBukkitClass("block.CraftBlock");
        nativeBlockDataAccess = MethodHandles.lookup().findVirtual(craftBukkitClass, "getNMS", MethodType.methodType(blockDataClass));
      } else {
        Class<?> blockClass = ReflectiveAccess.lookupServerClass("Block");
        Class<?> blockDataClass = ReflectiveAccess.lookupServerClass("IBlockData");
        Class<?> craftBukkitClass = ReflectiveAccess.lookupCraftBukkitClass("block.CraftBlock");
        Method getNMSBlockMethod = craftBukkitClass.getDeclaredMethod("getNMSBlock");
        getNMSBlockMethod.setAccessible(true);
        nativeBlockDataAccess = MethodHandles.lookup().unreflect(getNMSBlockMethod);
        nativeBlockDataExtractionAccess = MethodHandles.lookup().findVirtual(blockClass, "fromLegacyData", MethodType.methodType(blockDataClass, Integer.TYPE));
      }
    } catch (NoSuchMethodException | IllegalAccessException exception) {
      throw new IntaveInternalException("Failed to load data accessor", exception);
    }
    loadMaterials();
  }

  private static <T> T instanceOf(String className) {
    try {
      //noinspection unchecked
      return (T) Class.forName(className).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException exception) {
      throw new IntaveInternalException(exception);
    }
  }

  public static int dataAccess(Block block) {
    Material type = BlockTypeAccess.typeAccess(block);
    if (isLegacy(type)) {
      return block.getData();
    } else {
      return RuntimeBlockDataIndexer.indexOfModernState(type, nativeBlockDataOf(block));
    }
  }

  public static Object nativeBlockDataOf(Block bukkitBlock) {
    try {
      if (NEW_BLOCK_ACCESS) {
        return nativeBlockDataAccess.invoke(bukkitBlock);
      } else {
        return blockDataFromNativeBlock(bukkitBlock, nativeBlockDataAccess.invoke(bukkitBlock));
      }
    } catch (Throwable throwable) {
      throw new IntaveInternalException("Failed to access block data of " + bukkitBlock, throwable);
    }
  }

  public static Object blockDataFromNativeBlock(Block block, Object nativeBlock) {
    try {
      return nativeBlockDataExtractionAccess.invoke(nativeBlock, block.getData());
    } catch (Throwable throwable) {
      throw new IntaveInternalException("Failed to access block data of " + nativeBlock, throwable);
    }
  }

  public static boolean isLegacy(Material type) {
    return !NEW_MATERIAL_PROCESSING || legacyMaterials.contains(type);
  }

  public static boolean isClickable(Material type) {
    return clickableMaterials.contains(type);
  }

  public static float blockDamage(Player player, ItemStack itemInHand, BlockPosition blockPosition) {
    return blockAccessor.blockDamage(player, itemInHand, blockPosition);
  }

  public static boolean replacementPlace(World world, Player player, BlockPosition blockPosition) {
    return blockAccessor.replacementPlace(world, player, blockPosition);
  }

  private static void loadMaterials() {
    if (NEW_MATERIAL_PROCESSING) {
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
        if ((boolean) isLegacy.invoke(material)) {
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
