package de.jpx3.intave.detect.checks.example;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.wrapper.WrappedEnumDirection;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class WorkspaceCheck extends IntaveMetaCheck<WorkspaceCheck.WorkspaceMeta> {
  private final IntavePlugin plugin;

  public WorkspaceCheck(IntavePlugin plugin) {
    super("Test", "test", WorkspaceMeta.class);
    this.plugin = plugin;
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE")
    }
  )
  public void onPlace(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    BlockPosition blockPosition = packet.getBlockPositionModifier().readSafely(0);
    if(blockPosition == null) {
      return;
    }
    int enumDirection = packet.getIntegers().readSafely(0);
    if(enumDirection == 255) {
      return;
    }

    player.sendMessage("Hello");

    User user = userOf(player);

    World world = player.getWorld();
    Location targetLocation = blockPosition.toLocation(world);
    Location blockPlacementLocation = blockPosition.toLocation(world).add(WrappedEnumDirection.getFront(enumDirection).getDirectionVec().convertToBukkitVec());

    Material itemTypeInHand = player.getItemInHand().getType();
    boolean isPlacement = itemTypeInHand != Material.AIR && itemTypeInHand.isBlock();
    boolean permission = isPlacement && plugin.interactionPermissionService().blockPlacePermissionCheck().hasPermission(player, world, true, blockPlacementLocation.getBlockX(), blockPlacementLocation.getBlockY(), blockPlacementLocation.getBlockZ(), itemTypeInHand.getId(), (byte) 0);
    player.sendMessage(String.valueOf(permission));
  }

  public static class WorkspaceMeta extends UserCustomCheckMeta {

  }
}
