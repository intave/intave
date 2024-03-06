package de.jpx3.intave.block.access;

import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import net.minecraft.server.v1_13_R2.IBlockData;
import net.minecraft.server.v1_13_R2.Item;
import net.minecraft.server.v1_13_R2.WorldServer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_13_R2.block.CraftBlock;
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@PatchyAutoTranslation
public final class v13BlockAccessor implements BlockAccessor {
  @Override
  public Material typeOf(Block block) {
    return block.getType();
  }

  @Override
  public int variantIndexOf(Block block) {
    Material type = typeOf(block);
    IBlockData blockData = (IBlockData) nativeVariantOf(block);
    int variantIndex = BlockVariantRegister.variantIndexOf(type, blockData);
    return Math.max(variantIndex, 0);
  }

  @Override
  @PatchyAutoTranslation
  public Object nativeVariantOf(Block block) {
    return ((CraftBlock) block).getNMS();
  }

  @Override
  @PatchyAutoTranslation
  public Object nativeVariantBy(int blockId) {
    return net.minecraft.server.v1_13_R2.Block.getByCombinedId(blockId).getBlock();
  }

  @Override
  @PatchyAutoTranslation
  public float blockDamage(World world, Player player, ItemStack itemInHand, BlockPosition blockPosition) {
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    net.minecraft.server.v1_13_R2.BlockPosition blockposition = new net.minecraft.server.v1_13_R2.BlockPosition(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
    User user = UserRepository.userOf(player);
    Location location = blockPosition.toLocation(world);
    Material material = VolatileBlockAccess.typeAccess(user, location);
    int variant = VolatileBlockAccess.variantIndexAccess(user, location);
    IBlockData rawVariant = (IBlockData) BlockVariantRegister.rawVariantOf(material, variant);
    return rawVariant.getBlock().getDamage(rawVariant, player == null ? null : ((CraftPlayer) player).getHandle(), worldServer, blockposition);
  }

  @Override
  @PatchyAutoTranslation
  public boolean replacementPlace(World world, Player player, BlockPosition blockPosition) {
    User user = UserRepository.userOf(player);
    int heldItemType = user.meta().inventory().handSlot();
    Item heldItem = ((CraftPlayer) player).getHandle().inventory.getItem(heldItemType).getItem();
    Location location = blockPosition.toLocation(world);
    Material material = VolatileBlockAccess.typeAccess(user, location);
    int variant = VolatileBlockAccess.variantIndexAccess(user, location);
    IBlockData rawVariant = (IBlockData) BlockVariantRegister.rawVariantOf(material, variant);
    return rawVariant.getMaterial().isReplaceable() && rawVariant.getBlock().getItem().getItem() != heldItem;
  }
}
