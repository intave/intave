package de.jpx3.intave.world.block;

import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.reflect.ReflectionFailureException;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class BlockDataAccess {
  private static final BlockAccessor blockAccessor;

  private final static List<Material> clickableMaterials = new ArrayList<>();

  static {
    String resolverName = "de.jpx3.intave.world.block.LegacyBlockAccessor";

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
      throw new ReflectionFailureException(exception);
    }
  }

  private static void loadClickableMaterials() {
    Class<?> blockClass = ReflectiveAccess.lookupServerClass("Block");
    Method getByIdMethod;
    try {
      getByIdMethod = blockClass.getMethod("getById", Integer.TYPE);
    } catch (NoSuchMethodException exception) {
      throw new ReflectionFailureException(exception);
    }

    Class<?> world = ReflectiveAccess.lookupServerClass("World");
    Class<?> blockPosition = ReflectiveAccess.lookupServerClass("BlockPosition");
    Class<?> iBlockData = ReflectiveAccess.lookupServerClass("IBlockData");
    Class<?> entityHuman = ReflectiveAccess.lookupServerClass("EntityHuman");
    Class<?> enumDirection = ReflectiveAccess.lookupServerClass("EnumDirection");
    Class<Float> floatClass = Float.TYPE;

    // TODO: 01/10/21 check version availability

    try {
      for (int i = 0; i < 64000; i++) {
        Material material = Material.getMaterial(i);
        if(material == null) {
          continue;
        }
        Object block = getByIdMethod.invoke(null, i);
        if(block == null) {
          continue;
        }
        if(block.getClass().getMethod("interact", world, blockPosition, iBlockData, entityHuman, enumDirection, floatClass, floatClass, floatClass).getDeclaringClass() != blockClass) {
          clickableMaterials.add(material);
        }
      }
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
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
