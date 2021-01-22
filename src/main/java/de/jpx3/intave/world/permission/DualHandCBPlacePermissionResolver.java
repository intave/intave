package de.jpx3.intave.world.permission;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.BlockPlacePermissionCheck;
import de.jpx3.intave.event.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.patchy.annotate.PatchyTranslateParameters;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.reflect.ReflectionFailureException;
import net.minecraft.server.v1_9_R2.WorldServer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.v1_9_R2.CraftChunk;
import org.bukkit.craftbukkit.v1_9_R2.CraftServer;
import org.bukkit.craftbukkit.v1_9_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_9_R2.block.CraftBlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class DualHandCBPlacePermissionResolver implements BlockPlacePermissionCheck, BukkitEventSubscriber {
  @Override
  @PatchyAutoTranslation
  public boolean hasPermission(Player player, World world, boolean mainHand, int blockX, int blockY, int blockZ, int typeId, byte data) {
    if(world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
      CraftChunk chunk = (CraftChunk) world.getChunkAt(blockX >> 4, blockZ >> 4);
      CraftBlockState replacedBlockState = new CraftBlockState(new CustomCraftBlock(chunk, blockX, blockY, blockZ, typeId, data));
      WorldServer worldServer = ((CraftWorld) world).getHandle();
      CraftWorld craftWorld = worldServer.getWorld();
      CraftServer craftServer = worldServer.getServer();
      Block blockClicked = craftWorld.getBlockAt(blockX, blockY, blockZ);
      Block placedBlock = replacedBlockState.getBlock();
      boolean canBuild = canBuildReflectiveCall(craftWorld, player, placedBlock.getX(), placedBlock.getZ());
      ItemStack item;
      EquipmentSlot equipmentSlot;
      if (mainHand) {
        item = player.getInventory().getItemInMainHand();
        equipmentSlot = EquipmentSlot.HAND;
      } else {
        item = player.getInventory().getItemInOffHand();
        equipmentSlot = EquipmentSlot.OFF_HAND;
      }
      BlockPlaceEvent event = new PermissionCheckBlockPlaceEvent(placedBlock, replacedBlockState, blockClicked, item, player, canBuild, equipmentSlot);
      craftServer.getPluginManager().callEvent(event);
      return !event.isCancelled();
    }
    return false;
  }

  private Method canBuildMethod;

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private boolean canBuildReflectiveCall(CraftWorld world, Player player, int x, int z) {
    if(canBuildMethod == null) {
      try {
        canBuildMethod = ReflectiveAccess.lookupCraftBukkitClass("event.CraftEventFactory").getDeclaredMethod("canBuild", CraftWorld.class, Player.class, Integer.TYPE, Integer.TYPE);
        if(!canBuildMethod.isAccessible()) {
          canBuildMethod.setAccessible(true);
        }
      } catch (NoSuchMethodException exception) {
        throw new ReflectionFailureException(exception);
      }
    }
    try {
      return (boolean) canBuildMethod.invoke(null, world, player, x, z);
    } catch (IllegalAccessException | InvocationTargetException exception) {
      throw new ReflectionFailureException(exception);
    }
  }

  @Override
  public void open() {
    IntavePlugin.singletonInstance().eventLinker().registerEventsIn(this);
  }

  @Override
  public void close() {
    IntavePlugin.singletonInstance().eventLinker().unregisterEventsIn(this);
  }

  @BukkitEventSubscription(priority = EventPriority.LOWEST)
  public void onPre(BlockPlaceEvent place) {
    if(!(place instanceof PermissionCheckBlockPlaceEvent)) {
      place.setCancelled(true);
    }
  }

  @BukkitEventSubscription(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPost(BlockPlaceEvent place) {
    if(!(place instanceof PermissionCheckBlockPlaceEvent)) {
      place.setCancelled(false);
    }
  }


  public static class PermissionCheckBlockPlaceEvent extends BlockPlaceEvent {
    public PermissionCheckBlockPlaceEvent(Block placedBlock, BlockState replacedBlockState, Block placedAgainst, ItemStack itemInHand, Player thePlayer, boolean canBuild, EquipmentSlot hand) {
      super(placedBlock, replacedBlockState, placedAgainst, itemInHand, thePlayer, canBuild, hand);
    }
  }
}
