package de.jpx3.intave.world.blockaccess;

import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.reflect.ReflectiveBlockAccess;
import de.jpx3.intave.reflect.ReflectiveMaterialAccess;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BlockDataAccess {
  private static BlockAccessor blockAccessor;

  private final static List<Material> clickableMaterials = new ArrayList<>();

  public static void setup() {
    String resolverName = "de.jpx3.intave.world.blockaccess.v8BlockAccessor";
    if (MinecraftVersion.COMBAT_UPDATE.atOrAbove()) {
      resolverName = "de.jpx3.intave.world.blockaccess.v9BlockAccessor";
    }

    ClassLoader classLoader = IntavePlugin.class.getClassLoader();
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, resolverName);
    blockAccessor = instanceOf(resolverName);

    loadClickableMaterials();
  }

  private static <T> T instanceOf(String className) {
    try {
      //noinspection unchecked
      return (T) Class.forName(className).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException exception) {
      throw new IntaveInternalException(exception);
    }
  }

  private static void loadClickableMaterials() {
    /*
    class Block

    1.8   public boolean interact(World world, BlockPosition blockposition, IBlockData iblockdata, EntityHuman entityhuman,                                                   EnumDirection enumdirection, float f, float f1, float f2)
    1.9   public boolean interact(World world, BlockPosition blockposition, IBlockData iblockdata, EntityHuman entityhuman, EnumHand enumhand, @Nullable ItemStack itemstack, EnumDirection enumdirection, float f, float f1, float f2)
    1.10  public boolean interact(World world, BlockPosition blockposition, IBlockData iblockdata, EntityHuman entityhuman, EnumHand enumhand, @Nullable ItemStack itemstack, EnumDirection enumdirection, float f, float f1, float f2)
    1.11  public boolean interact(World world, BlockPosition blockposition, IBlockData iblockdata, EntityHuman entityhuman, EnumHand enumhand,                                EnumDirection enumdirection, float f, float f1, float f2)
    1.12  public boolean interact(World world, BlockPosition blockposition, IBlockData iblockdata, EntityHuman entityhuman, EnumHand enumhand,                                EnumDirection enumdirection, float f, float f1, float f2)
    1.13  public boolean interact(IBlockData iblockdata, World world, BlockPosition blockposition, EntityHuman entityhuman, EnumHand enumhand, EnumDirection enumdirection, float f, float f1, float f2) @D
    1.14  public boolean interact(IBlockData iblockdata, World world, BlockPosition blockposition, EntityHuman entityhuman, EnumHand enumhand, MovingObjectPositionBlock movingobjectpositionblock) @D
    1.15  public EnumInteractionResult interact(IBlockData iblockdata, World world, BlockPosition blockposition, EntityHuman entityhuman, EnumHand enumhand, MovingObjectPositionBlock movingobjectpositionblock) @D

    class BlockBase

    1.16  public EnumInteractionResult interact(IBlockData iblockdata, World world, BlockPosition blockposition, EntityHuman entityhuman, EnumHand enumhand, MovingObjectPositionBlock movingobjectpositionblock) @D

     */

    // TODO: 4/4/2021 add option for 1.16

    for (int i = 0; i < 1000; i++) {
      Material material = ReflectiveMaterialAccess.materialById(i);
      if (material == null) {
        continue;
      }
      Object block = ReflectiveBlockAccess.blockById(i);
      if (block == null) {
        IntaveLogger.logger().globalPrintLn("No block found for id " + i);
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

  public static List<Method> allMethodsIn(Class<?> clazz) {
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

  public static boolean isClickable(Material type) {
    return clickableMaterials.contains(type);
  }

  public static float blockDamage(Player player, ItemStack itemInHand, BlockPosition blockPosition) {
    return blockAccessor.blockDamage(player, itemInHand, blockPosition);
  }

  public static boolean replacementPlace(World world, BlockPosition blockPosition) {
    return blockAccessor.replacementPlace(world, blockPosition);
  }
}
