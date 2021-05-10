package de.jpx3.intave.access.player.event;

import de.jpx3.intave.access.IntaveEvent;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;

public final class AsyncIntaveInteractionPermissionEvent extends IntaveEvent implements Cancellable {
  private Player player;
  private Action action;
  private ItemStack itemStack;
  private Block block;
  private BlockFace blockFace;
  private boolean cancelled;

  private AsyncIntaveInteractionPermissionEvent() {
  }

  public void copy(
    Player player,
    Action action,
    ItemStack itemStack,
    Block block,
    BlockFace blockFace
  ) {
    this.player = player;
    this.action = action;
    this.itemStack = itemStack;
    this.block = block;
    this.blockFace = blockFace;
    this.cancelled = false;
  }

  public Player player() {
    return player;
  }

  public Action action() {
    return action;
  }

  public ItemStack itemStack() {
    return itemStack;
  }

  public Block block() {
    return block;
  }

  public BlockFace blockFace() {
    return blockFace;
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public void setCancelled(boolean cancelled) {
    this.cancelled = cancelled;
  }

  @Override
  public void referenceInvalidate() {
    player = null;
    block = null;
  }

  public static AsyncIntaveInteractionPermissionEvent empty() {
    return new AsyncIntaveInteractionPermissionEvent();
  }
}
