package de.jpx3.intave.block.access;

import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.klass.rewrite.PatchyTranslateParameters;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import net.minecraft.server.v1_14_R1.*;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_14_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_14_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_14_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

@PatchyAutoTranslation
public final class v14BlockAccessor implements BlockAccessor {
  private static final boolean INVENTORY_VIA_GETTER = MinecraftVersions.VER1_17_0.atOrAbove();

  static {
    checkVersion();
  }

  @PatchyAutoTranslation
  public static void checkVersion() {
    try {
      ChunkProviderServer.class.getMethod("c", int.class, int.class);
    } catch (NoSuchMethodException exception) {
      throw new IllegalStateException("Please update your minecraft version", exception);
    }
  }

  @Override
  @PatchyAutoTranslation
  public Material typeOf(Block block) {
    WorldServer worldServer = ((CraftWorld) block.getWorld()).getHandle();
    IBlockAccess blockAccess = worldServer.getChunkProvider().c(block.getX() >> 4, block.getZ() >> 4);
    if (blockAccess == null) {
      return Material.AIR;
    }
    IBlockData blockData = blockAccess.getType(positionOfBlock(block));
    net.minecraft.server.v1_14_R1.Block nmsBlock = blockData.getBlock();
    return CraftMagicNumbers.getMaterial(nmsBlock);
  }

  @Override
  @PatchyAutoTranslation
  public int variantIndexOf(Block block) {
    Material type = typeOf(block);
    IBlockData blockData = (IBlockData) nativeVariantOf(block);
    int variantIndex = BlockVariantRegister.variantIndexOf(type, blockData);
    return Math.max(variantIndex, 0);
  }

  @Override
  @PatchyAutoTranslation
  public Object nativeVariantOf(Block block) {
    WorldServer worldServer = ((CraftWorld) block.getWorld()).getHandle();
    IBlockAccess blockAccess = worldServer.getChunkProvider().c(block.getX() >> 4, block.getZ() >> 4);
    if (blockAccess == null) {
      return Blocks.AIR.getBlockData();
    }
    return blockAccess.getType(positionOfBlock(block));
  }

  @Override
  @PatchyAutoTranslation
  public Object nativeVariantBy(int blockId) {
    return net.minecraft.server.v1_14_R1.Block.getByCombinedId(blockId).getBlock();
  }

  @Override
  @PatchyAutoTranslation
  public float blockDamage(Player player, ItemStack itemInHand, BlockPosition nativeBlockPosition) {
    WorldServer worldServer = ((CraftWorld) player.getWorld()).getHandle();
    IBlockAccess blockAccess = worldServer.getChunkProvider().c(nativeBlockPosition.getX() >> 4, nativeBlockPosition.getZ() >> 4);
    if (blockAccess == null) {
      return 0.0f;
    }
    net.minecraft.server.v1_14_R1.BlockPosition blockPosition = positionOfNative(nativeBlockPosition);
    IBlockData blockData = blockAccess.getType(blockPosition);
    return blockData.getBlock().getDamage(blockData, ((CraftPlayer) player).getHandle(), blockAccess, blockPosition);
  }

  @Override
  @PatchyAutoTranslation
  public boolean replacementPlace(World world, Player player, BlockPosition nativeBlockPosition) {
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    IBlockAccess blockAccess = worldServer.getChunkProvider().c(nativeBlockPosition.getX() >> 4, nativeBlockPosition.getZ() >> 4);
    if (blockAccess == null) {
      return false;
    }
    User user = UserRepository.userOf(player);
    int heldSlot = user.meta().inventory().handSlot();
    IBlockData blockData = blockAccess.getType(positionOfNative(nativeBlockPosition));
    Object heldItem;
    if (INVENTORY_VIA_GETTER) {
      heldItem = ((org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer) player).getHandle().getInventory().getItem(heldSlot).getItem();
    } else {
      heldItem = ((CraftPlayer) player).getHandle().inventory.getItem(heldSlot).getItem();
    }
    Item targetItem = blockData.getBlock().getItem();
    return blockData.getMaterial().isReplaceable() && !Objects.equals(targetItem, heldItem);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private net.minecraft.server.v1_14_R1.BlockPosition positionOfBlock(Block block) {
    return new net.minecraft.server.v1_14_R1.BlockPosition(block.getX(), block.getY(), block.getZ());
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private net.minecraft.server.v1_14_R1.BlockPosition positionOfNative(BlockPosition blockPosition) {
    return new net.minecraft.server.v1_14_R1.BlockPosition(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
  }
}
