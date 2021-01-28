package de.jpx3.intave.world.block;

import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.reflect.ReflectionFailureException;
import de.jpx3.intave.reflect.ReflectiveAccess;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class BlockDataAccess {
  private static BlockAccessor blockAccessor;

  private final static List<Material> clickableMaterials = new ArrayList<>();

  public static void setup() {
    String resolverName = "de.jpx3.intave.world.block.v8BlockAccessor";

    if(MinecraftVersion.COMBAT_UPDATE.atOrAbove()) {
      resolverName = "de.jpx3.intave.world.block.v9BlockAccessor";
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
        Method[] methods = block.getClass().getDeclaredMethods();
        for (Method method : methods) {
          if(method.getName().equalsIgnoreCase("interact")) {
            String declaringClassName = method.getDeclaringClass().getSimpleName();
            if(!declaringClassName.equals("Block") && !declaringClassName.equals("BlockBase")) {
              clickableMaterials.add(material);
            }
          }
        }
      }
    } catch (IllegalAccessException | InvocationTargetException e) {
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
