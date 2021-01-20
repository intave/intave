package de.jpx3.intave.world.permission;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.BlockBreakPermissionCheck;
import de.jpx3.intave.event.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;

public final class CBBreakPermissionResolver implements BlockBreakPermissionCheck, BukkitEventSubscriber {
  @Override
  public boolean hasPermission(Player player, Block block) {
    BlockBreakEvent blockBreakEvent = new PermissionCheckBlockBreakEvent(block, player);
    //Bukkit.getPluginManager().callEvent(blockBreakEvent);
    IntavePlugin.singletonInstance().eventLinker().fireExternalEvent(blockBreakEvent);
    return !blockBreakEvent.isCancelled();
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
  public void onPre(BlockBreakEvent breakEvent) {
    if(!(breakEvent instanceof PermissionCheckBlockBreakEvent)) {
      breakEvent.setCancelled(true);
    }
  }

  @BukkitEventSubscription(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPost(BlockBreakEvent breakEvent) {
    if(!(breakEvent instanceof PermissionCheckBlockBreakEvent)) {
      breakEvent.setCancelled(false);
    }
  }

  public static class PermissionCheckBlockBreakEvent extends BlockBreakEvent {
    public PermissionCheckBlockBreakEvent(Block theBlock, Player player) {
      super(theBlock, player);
    }
  }
}
