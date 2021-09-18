package de.jpx3.intave.world.permission;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.event.AsyncIntaveInteractionPermissionEvent;
import de.jpx3.intave.module.Modules;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;

public final class EventInteractionPermissionResolver implements BlockInteractionPermissionCheck {
  private final IntavePlugin plugin;

  public EventInteractionPermissionResolver(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean hasPermission(Player player, Action action, ItemStack itemStack, Block block, BlockFace blockFace) {
    AsyncIntaveInteractionPermissionEvent event = Modules.eventInvoker().invokeEvent(
      AsyncIntaveInteractionPermissionEvent.class,
      x -> x.copy(player, action, itemStack, block, blockFace)
    );
    return !event.isCancelled();
  }
}
