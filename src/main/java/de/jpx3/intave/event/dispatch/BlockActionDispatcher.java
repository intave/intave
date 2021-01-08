package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.event.packet.*;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.wrapper.WrappedEnumDirection;
import de.jpx3.intave.world.BlockAccessor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK;

public final class BlockActionDispatcher implements PacketEventSubscriber {
  private final IntavePlugin plugin;

  public BlockActionDispatcher(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ITEM")
    }
  )
  public void receiveInteraction(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    BlockPosition blockPosition = packet.getBlockPositionModifier().readSafely(0);
    if(blockPosition == null) {
      return;
    }
    Integer enumDirection = packet.getIntegers().readSafely(0);
    if(enumDirection == null) {
      enumDirection = packet.getDirections().readSafely(0).ordinal();
    }
    if(enumDirection == 255) {
      return;
    }

    if(event.isCancelled()) {
      return;
    }

    World world = player.getWorld();
    Location blockPlacementLocation = blockPosition.toLocation(world).add(WrappedEnumDirection.getFront(enumDirection).getDirectionVec().convertToBukkitVec());
    Material itemTypeInHand = player.getItemInHand().getType();
    boolean isPlacement = itemTypeInHand != Material.AIR && itemTypeInHand.isBlock();

    if(isPlacement) {
      final int blockX = blockPlacementLocation.getBlockX();
      final int blockY = blockPlacementLocation.getBlockY();
      final int blockZ = blockPlacementLocation.getBlockZ();

      EnumWrappers.Hand hand = packet.getHands().readSafely(0);
      boolean access = plugin.interactionPermissionService()
        .blockPlacePermissionCheck()
        .hasPermission(
          player,
          world,
          hand == null || hand == EnumWrappers.Hand.MAIN_HAND,
          blockX, blockY, blockZ,
          itemTypeInHand.getId(),
          (byte) 0
        );

      if(access) {
        // add to future bounding boxes

        player.sendMessage("Block-placement confirmation for " + MathHelper.formatPosition(blockPlacementLocation));
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_DIG")
    }
  )
  public void receiveBreak(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    BlockPosition blockPosition = packet.getBlockPositionModifier().readSafely(0);
    if(blockPosition == null) {
      return;
    }
    EnumWrappers.PlayerDigType playerDigType = packet.getPlayerDigTypes().readSafely(0);
    if(!(playerDigType == STOP_DESTROY_BLOCK)) {
      return;
    }
    EnumWrappers.Direction direction = packet.getDirections().readSafely(0);
    int enumDirection = direction == null ? 255 : direction.ordinal();
    if(enumDirection == 255) {
      return;
    }

    if(event.isCancelled()) {
      return;
    }

    World world = player.getWorld();
    Location blockBreakLocation = blockPosition.toLocation(world);

    boolean access = plugin.interactionPermissionService()
      .blockBreakPermissionCheck()
      .hasPermission(
        player,
        BlockAccessor.blockAccess(blockBreakLocation)
      );

    if(access) {
      // add to future bounding boxes

      player.sendMessage("Block-break confirmation for " + MathHelper.formatPosition(blockBreakLocation));
    }
  }
}
