package de.jpx3.intave.world.permission;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.event.AsyncIntaveBlockBreakPermissionEvent;
import de.jpx3.intave.module.Modules;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class EventBreakPermissionResolver implements BlockBreakPermissionCheck {
  private final IntavePlugin plugin;

  public EventBreakPermissionResolver(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean hasPermission(Player player, Block block) {
    AsyncIntaveBlockBreakPermissionEvent event = Modules.eventInvoker().invokeEvent(
      AsyncIntaveBlockBreakPermissionEvent.class,
      x -> x.copy(player, block)
    );
    return !event.isCancelled();
  }
}
