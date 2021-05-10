package de.jpx3.intave.access.player.event;

import de.jpx3.intave.access.IntaveEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;

public final class AsyncIntaveBukkitActionPermissionEvent extends IntaveEvent implements Cancellable {
  private Player player;
  private BucketAction bucketAction;
  private Block blockClicked;
  private BlockFace blockFace;
  private Material bucket;
  private ItemStack itemInHand;
  private boolean cancelled;

  protected AsyncIntaveBukkitActionPermissionEvent() {
  }

  public void copy(
    Player player,
    BucketAction bucketAction,
    Block blockClicked,
    BlockFace blockFace,
    Material bucket,
    ItemStack itemInHand
  ) {
    this.player = player;
    this.bucketAction = bucketAction;
    this.blockClicked = blockClicked;
    this.blockFace = blockFace;
    this.bucket = bucket;
    this.itemInHand = itemInHand;
    this.cancelled = false;
  }

  public Player player() {
    return player;
  }

  public BucketAction bucketAction() {
    return bucketAction;
  }

  public Block blockClicked() {
    return blockClicked;
  }

  public BlockFace blockFace() {
    return blockFace;
  }

  public Material bucket() {
    return bucket;
  }

  public ItemStack itemInHand() {
    return itemInHand;
  }

  @Override
  public void referenceInvalidate() {
    player = null;
    blockClicked = null;
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public void setCancelled(boolean cancelled) {
    this.cancelled = cancelled;
  }

  public static AsyncIntaveBukkitActionPermissionEvent empty() {
    return new AsyncIntaveBukkitActionPermissionEvent();
  }
}
