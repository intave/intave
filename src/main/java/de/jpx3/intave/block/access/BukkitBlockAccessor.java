package de.jpx3.intave.block.access;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.world.WorldHeight;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

final class BukkitBlockAccessor implements BlockAccessor {
  private Method breakSpeedMethod;
  private Method blockDataMethod;
  private Method replaceableMethod;
  private Method hardnessMethod;

  @Override
  public Material typeOf(Block block) {
    int blockY = block.getY();
    if (blockY < WorldHeight.LOWER_WORLD_LIMIT || blockY > WorldHeight.UPPER_WORLD_LIMIT) {
      return Material.AIR;
    }
    return block.getType();
  }

  @Override
  public int variantIndexOf(Block block) {
    int blockY = block.getY();
    if (blockY < WorldHeight.LOWER_WORLD_LIMIT || blockY > WorldHeight.UPPER_WORLD_LIMIT) {
      return 0;
    }
    return BlockVariantNativeAccess.variantAccess(block);
  }

  @Override
  public Object nativeVariantOf(Block block) {
    return BlockVariantNativeAccess.nativeVariantAccess(block);
  }

  @Override
  public Object nativeVariantBy(int blockId) {
    ClientVersion version = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    return WrappedBlockState.getByGlobalId(version, blockId);
  }

  @Override
  public float blockDamage(World world, Player player, ItemStack itemInHand, BlockPosition blockPosition) {
    Block block = world.getBlockAt(blockPosition.getBlockX(), blockPosition.getBlockY(), blockPosition.getBlockZ());
    Float serverBreakSpeed = breakSpeed(block, player);
    if (serverBreakSpeed != null) {
      return serverBreakSpeed;
    }
    Material material = block.getType();
    float hardness = hardnessOf(material);
    if (hardness <= 0.0F) {
      return 1.0F;
    }
    return 1.0F / (hardness * 30.0F);
  }

  @Override
  public boolean replacementPlace(World world, Player player, BlockPosition blockPosition) {
    Block block = world.getBlockAt(blockPosition.getBlockX(), blockPosition.getBlockY(), blockPosition.getBlockZ());
    Material target = block.getType();
    Material held = player == null || player.getItemInHand() == null ? Material.AIR : player.getItemInHand().getType();
    return isReplaceable(block, target) && target != held;
  }

  private Float breakSpeed(Block block, Player player) {
    if (player == null) {
      return null;
    }
    try {
      Method method = breakSpeedMethod;
      if (method == null) {
        method = block.getClass().getMethod("getBreakSpeed", Player.class);
        breakSpeedMethod = method;
      }
      return ((Number) method.invoke(block, player)).floatValue();
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  private boolean isReplaceable(Block block, Material material) {
    try {
      Method blockData = blockDataMethod;
      if (blockData == null) {
        blockData = block.getClass().getMethod("getBlockData");
        blockDataMethod = blockData;
      }
      Object data = blockData.invoke(block);
      Method replaceable = replaceableMethod;
      if (replaceable == null) {
        replaceable = data.getClass().getMethod("isReplaceable");
        replaceableMethod = replaceable;
      }
      return (boolean) replaceable.invoke(data);
    } catch (ReflectiveOperationException ignored) {
      String name = material.name();
      return "AIR".equals(name)
        || "CAVE_AIR".equals(name)
        || "VOID_AIR".equals(name)
        || "WATER".equals(name)
        || "LAVA".equals(name)
        || "FIRE".equals(name)
        || name.endsWith("_GRASS")
        || name.endsWith("_SAPLING")
        || name.endsWith("_FLOWER")
        || name.endsWith("_MUSHROOM")
        || name.endsWith("_CARPET")
        || "SNOW".equals(name)
        || "VINE".equals(name);
    }
  }

  private float hardnessOf(Material material) {
    try {
      Method method = hardnessMethod;
      if (method == null) {
        method = Material.class.getMethod("getHardness");
        hardnessMethod = method;
      }
      return ((Number) method.invoke(material)).floatValue();
    } catch (ReflectiveOperationException ignored) {
      return material.isSolid() ? 1.5F : 0.0F;
    }
  }
}
