package de.jpx3.intave.world.permission;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.event.AsyncIntaveBlockPlacePermissionEvent;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class EventPlacePermissionResolver implements BlockPlacePermissionCheck {
  private final IntavePlugin plugin;

  public EventPlacePermissionResolver(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean hasPermission(
    Player player, World world,
    boolean mainHand,
    int blockX, int blockY, int blockZ,
    int enumDirection, Material type, byte data
  ) {
    AsyncIntaveBlockPlacePermissionEvent event = plugin.customEventService().invokeEvent(
      AsyncIntaveBlockPlacePermissionEvent.class,
      x -> x.copy(player, world, mainHand, blockX, blockY, blockZ, enumDirection, type, data)
    );
    return !event.isCancelled();
  }
}
