package de.jpx3.intave.detect.checks.world;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.detect.checks.world.interaction.BlockRaytracer;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.wrapper.WrappedBlockPosition;
import de.jpx3.intave.tools.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.START_DESTROY_BLOCK;
import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK;

public final class InteractionRaytrace extends IntaveMetaCheck<InteractionRaytrace.InteractionMeta> {
  public InteractionRaytrace() {
    super("InteractionRaytrace", "interactionRaytrace", InteractionMeta.class);
  }

  // break
  // 1st invalid -> save packet and queue packet for 2nd move (cancel)

  // interact
  // 1st invalid -> save raytrace and queue packet for 2nd move packet (raytrace override)

  // place
  // 1st invalid -> save raytrace and queue packet for 2nd move packet (raytrace override)


  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE")
    }
  )
  public void receiveInteraction(PacketEvent event) {
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
    User user = userOf(player);
    UserMetaMovementData userMetaMovementData = user.meta().movementData();
    World world = player.getWorld();
    Location targetLocation = blockPosition.toLocation(world);
    Location playerLocation = UserRepository.userOf(player).meta().movementData().verifiedLocation().clone();
    playerLocation.setYaw(userMetaMovementData.rotationYaw);
    playerLocation.setPitch(userMetaMovementData.rotationPitch);
    WrappedMovingObjectPosition raycastResult = BlockRaytracer.resolveBlockInLineOfSight(player, playerLocation);
    boolean hitMiss = raycastResult == null || raycastResult.hitVec == WrappedVector.ZERO;
    WrappedBlockPosition raycastVector = hitMiss ? WrappedBlockPosition.ORIGIN : raycastResult.getBlockPos();
    Location raycastLocation = raycastVector.toLocation(world);
    boolean isPlacement = player.getItemInHand().getType().isBlock();
    if((raycastResult == null || enumDirection != raycastResult.sideHit.getIndex()) || raycastLocation.distance(targetLocation) > 0) {
      metaOf(player).traceReportList.add(
        new TraceReport(raycastVector,
          packet.deepClone(),
          playerLocation,
          isPlacement ? InteractionType.PLACE : InteractionType.INTERACT,
          0
        )
      );
      event.setCancelled(true);
    }

    Thread.dumpStack();
  }

  @PacketSubscription(
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
    int enumDirection = packet.getDirections().read(0).ordinal();
    if(enumDirection == 255) {
      return;
    }
    EnumWrappers.PlayerDigType playerDigType = packet.getPlayerDigTypes().readSafely(0);
    if(!(playerDigType == START_DESTROY_BLOCK || playerDigType == STOP_DESTROY_BLOCK)) {
      return;
    }
    User user = userOf(player);
    UserMetaMovementData userMetaMovementData = user.meta().movementData();
    World world = player.getWorld();
    Location targetLocation = blockPosition.toLocation(world);
    Location playerLocation = UserRepository.userOf(player).meta().movementData().verifiedLocation().clone();
    playerLocation.setYaw(userMetaMovementData.rotationYaw);
    playerLocation.setPitch(userMetaMovementData.rotationPitch);
    WrappedMovingObjectPosition raycastResult = BlockRaytracer.resolveBlockInLineOfSight(player, playerLocation);
    boolean hitMiss = raycastResult == null || raycastResult.hitVec == WrappedVector.ZERO;
    WrappedBlockPosition raycastVector = hitMiss ? WrappedBlockPosition.ORIGIN : raycastResult.getBlockPos();
    Location raycastLocation = raycastVector.toLocation(world);
    if((raycastResult == null || enumDirection != raycastResult.sideHit.getIndex()) || raycastLocation.distance(targetLocation) > 0) {
      metaOf(player).traceReportList.add(
        new TraceReport(raycastVector,
          packet.deepClone(),
          playerLocation,
          InteractionType.BREAK,
          0
        )
      );
      event.setCancelled(true);
    }
  }

  public void receivePositionUpdate(float yaw, float pitch, double posX, double posY, double posZ) {

  }

  private void refreshBlock(Player player, Location location) {
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_CHANGE);
    Block block = location.getBlock();
    WrappedBlockData blockData = WrappedBlockData.createData(block.getType(), block.getData());
    BlockPosition position = new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    packet.getBlockData().write(0, blockData);
    packet.getBlockPositionModifier().write(0, position);
    try {
      ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    } catch (InvocationTargetException exception) {
      exception.printStackTrace();
    }
  }

  @BukkitEventSubscription
  public void receiveInteraction(BlockBreakEvent event) {
    Player player = event.getPlayer();
    WrappedMovingObjectPosition movingObjectPosition = BlockRaytracer.resolveBlockInLineOfSight(player, player.getLocation());

    boolean invalid = false;
    if(movingObjectPosition == null) {
      invalid = true;
    } else {
      Location location = movingObjectPosition.getBlockPos().toLocation(player.getWorld());
      double distance = location.distance(event.getBlock().getLocation());
//      player.sendMessage(String.valueOf(distance) + location.getBlock());
      if(distance > 0) {
        invalid = true;
      }
    }
    if(invalid) {
      event.setCancelled(true);
    }
  }

  public static class InteractionMeta extends UserCustomCheckMeta {
    double localVL; // please conv me into general vl for check
    private final List<TraceReport> traceReportList = new ArrayList<>();

  }

  public enum InteractionType {
    PLACE(ResponseType.RAYTRACE_ALTERNATIVE),
    BREAK(ResponseType.CANCEL),
    INTERACT(ResponseType.RAYTRACE_ALTERNATIVE);

    final ResponseType response;

    InteractionType(ResponseType response) {
      this.response = response;
    }

    public ResponseType response() {
      return response;
    }
  }

  public enum ResponseType {
    RAYTRACE_ALTERNATIVE,
    CANCEL
  }

  public static class TraceReport {
    private final WrappedBlockPosition raytraceResult;
    private final PacketContainer thePacket;
    private final Location contextPosition;
    private final InteractionType type;
    private final int addedVL;

    public TraceReport(
      WrappedBlockPosition raytraceResult,
      PacketContainer thePacket,
      Location contextPosition,
      InteractionType type, int addedVL
    ) {
      this.raytraceResult = raytraceResult;
      this.thePacket = thePacket;
      this.contextPosition = contextPosition;
      this.type = type;
      this.addedVL = addedVL;
    }

    public WrappedBlockPosition raytraceResult() {
      return raytraceResult;
    }

    public PacketContainer thePacket() {
      return thePacket;
    }

    public Location contextPosition() {
      return contextPosition;
    }

    public InteractionType type() {
      return type;
    }

    public int addedVL() {
      return addedVL;
    }
  }
}
