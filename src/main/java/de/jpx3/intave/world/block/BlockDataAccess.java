package de.jpx3.intave.world.block;

import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.reflect.ReflectionFailureException;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class BlockDataAccess {
  private static final BlockAccessor blockAccessor;

  static {
    String resolverName = "de.jpx3.intave.world.block.LegacyBlockAccessor";

    ClassLoader classLoader = IntavePlugin.class.getClassLoader();
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, resolverName);
    blockAccessor = instanceOf(resolverName);
  }

  private static <T> T instanceOf(String className) {
    try {
      //noinspection unchecked
      return (T) Class.forName(className).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException exception) {
      throw new ReflectionFailureException(exception);
    }
  }

  public static float blockDamage(Player player, ItemStack itemInHand, BlockPosition blockPosition) {
    return blockAccessor.blockDamage(player, itemInHand, blockPosition);
  }

  public static boolean replacementPlace(World world, BlockPosition blockPosition) {
    return blockAccessor.replacementPlace(world, blockPosition);
  }
}
