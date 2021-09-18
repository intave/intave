package de.jpx3.intave.world.permission;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.event.AsyncIntaveBukkitActionPermissionEvent;
import de.jpx3.intave.access.player.event.BucketAction;
import de.jpx3.intave.module.Modules;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class EventBukkitActionPermissionResolver implements BucketActionPermissionCheck {
  private final IntavePlugin plugin;

  public EventBukkitActionPermissionResolver(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean hasPermission(
    Player player,
    BucketAction bucketAction, Block blockClicked,
    BlockFace blockFace, Material bucket, ItemStack itemInHand
  ) {
    AsyncIntaveBukkitActionPermissionEvent event = Modules.eventInvoker().invokeEvent(
      AsyncIntaveBukkitActionPermissionEvent.class,
      x -> x.copy(player, bucketAction, blockClicked, blockFace, bucket, itemInHand)
    );
    return !event.isCancelled();
  }
}
