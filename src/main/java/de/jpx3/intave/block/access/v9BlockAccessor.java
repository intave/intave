package de.jpx3.intave.block.access;

import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import net.minecraft.server.v1_9_R2.IBlockData;
import net.minecraft.server.v1_9_R2.WorldServer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_9_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_9_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@PatchyAutoTranslation
public final class v9BlockAccessor implements BlockAccessor {
  @Override
  public Material typeOf(Block block) {
    return block.getType();
  }

  @Override
  public int variantIndexOf(Block block) {
    // up to 1.12.2 this is correct
    return block.getData();
  }

  @Override
  @PatchyAutoTranslation
  public Object nativeVariantOf(Block block) {
    Material type = block.getType();
    byte variant = block.getData();
    return net.minecraft.server.v1_9_R2.Block.getByCombinedId(type.getId() | (variant & 0xF) << 12);
  }

  @Override
  @PatchyAutoTranslation
  public Object nativeVariantBy(int blockId) {
    return net.minecraft.server.v1_9_R2.Block.getById(blockId);
  }

  @Override
  @PatchyAutoTranslation
  public float blockDamage(World world, Player player, ItemStack itemInHand, BlockPosition blockPosition) {
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    net.minecraft.server.v1_9_R2.BlockPosition blockposition = new net.minecraft.server.v1_9_R2.BlockPosition(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
    User user = UserRepository.userOf(player);
    Location location = blockPosition.toLocation(world);
    Material material = VolatileBlockAccess.typeAccess(user, location);
    int variant = VolatileBlockAccess.variantIndexAccess(user, location);
    IBlockData rawVariant = (IBlockData) BlockVariantRegister.rawVariantOf(material, variant);
    return rawVariant.getBlock().getDamage(rawVariant, ((CraftPlayer) player).getHandle(), worldServer, blockposition);
  }

  @Override
  @PatchyAutoTranslation
  public boolean replacementPlace(World world, Player player, BlockPosition blockPosition) {
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    net.minecraft.server.v1_9_R2.BlockPosition blockposition = new net.minecraft.server.v1_9_R2.BlockPosition(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
    User user = UserRepository.userOf(player);
    Location location = blockPosition.toLocation(world);
    Material material = VolatileBlockAccess.typeAccess(user, location);
    int variant = VolatileBlockAccess.variantIndexAccess(user, location);
    IBlockData rawVariant = (IBlockData) BlockVariantRegister.rawVariantOf(material, variant);
    return rawVariant.getBlock().a(worldServer, blockposition);
  }
}
