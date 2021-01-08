package de.jpx3.intave.detect.checks.world;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.common.collect.Maps;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedBlockPosition;
import de.jpx3.intave.tools.wrapper.WrappedEnumDirection;
import de.jpx3.intave.tools.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.world.BlockAccessor;
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK;

public final class InteractionRaytrace extends IntaveMetaCheck<InteractionRaytrace.InteractionMeta> {
  private final IntavePlugin plugin;

  public InteractionRaytrace(IntavePlugin plugin) {
    super("InteractionRaytrace", "interactionRaytrace", InteractionMeta.class);
    this.plugin = plugin;
  }

  // break
  // 1st invalid -> save packet and queue packet for 2nd move (--> cancel)

  // interact
  // 1st invalid -> save raytrace and queue packet for 2nd move packet (--> raytrace override)

  // place
  // 1st invalid -> save raytrace and queue packet for 2nd move packet (--> raytrace override)


  @PacketSubscription(
    priority = ListenerPriority.LOW,
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

    User user = userOf(player);
    InteractionMeta interactionMeta = metaOf(user);
    UserMetaMovementData movementData = user.meta().movementData();
    World world = player.getWorld();
    Location targetLocation = blockPosition.toLocation(world);
    Location blockPlacementLocation = blockPosition.toLocation(world).add(WrappedEnumDirection.getFront(enumDirection).getDirectionVec().convertToBukkitVec());
    Location playerLocation = movementData.verifiedLocation().clone();
    playerLocation.setYaw(movementData.rotationYaw);
    playerLocation.setPitch(movementData.rotationPitch);
    WrappedMovingObjectPosition raycastResult = Raytracer.blockRayTrace(player, playerLocation);
    boolean hitMiss = raycastResult == null || raycastResult.hitVec == WrappedVector.ZERO;
    WrappedBlockPosition raycastVector = hitMiss ? WrappedBlockPosition.ORIGIN : raycastResult.getBlockPos();
    Location raycastLocation = raycastVector.toLocation(world);
    Material itemTypeInHand = player.getItemInHand().getType();
    boolean isPlacement = itemTypeInHand != Material.AIR && itemTypeInHand.isBlock();

    InteractionType type = isPlacement ? InteractionType.PLACE : InteractionType.INTERACT;
    if((raycastResult == null || enumDirection != raycastResult.sideHit.getIndex()) || raycastLocation.distance(targetLocation) > 0) {
      boolean cancel = processViolation(player, type);
      // cancel if placement impossible

      if(BlockAccessor.blockAccess(raycastLocation).getType() == Material.AIR) {
        cancel = true;
      }

      interactionMeta.traceReportList.add(
        new TraceReport(
          raycastResult, blockPosition, enumDirection, packet.deepClone(),
          playerLocation, type,
          cancel
        )
      );
      if(cancel) {
        event.setCancelled(true);
      }
    } else {
      lowerViolation(player, type);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
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
    if(!(/*playerDigType == START_DESTROY_BLOCK || */playerDigType == STOP_DESTROY_BLOCK)) {
      return;
    }
    EnumWrappers.Direction direction = packet.getDirections().readSafely(0);
    int enumDirection = direction == null ? 255 : direction.ordinal();
    if(enumDirection == 255) {
      return;
    }
    User user = userOf(player);
    InteractionMeta interactionMeta = metaOf(user);

    UserMetaMovementData movementData = user.meta().movementData();
    World world = player.getWorld();
    Location targetLocation = blockPosition.toLocation(world);
    Location playerLocation = movementData.verifiedLocation().clone();
    playerLocation.setYaw(movementData.rotationYaw);
    playerLocation.setPitch(movementData.rotationPitch);
    WrappedMovingObjectPosition raycastResult = Raytracer.blockRayTrace(player, playerLocation);
    boolean hitMiss = raycastResult == null || raycastResult.hitVec == WrappedVector.ZERO;
    WrappedBlockPosition raycastVector = hitMiss ? WrappedBlockPosition.ORIGIN : raycastResult.getBlockPos();
    Location raycastLocation = raycastVector.toLocation(world);
    if((raycastResult == null || enumDirection != raycastResult.sideHit.getIndex()) || raycastLocation.distance(targetLocation) > 0) {
      boolean cancel = processViolation(player, InteractionType.BREAK);
      interactionMeta.traceReportList.add(
        new TraceReport(
          raycastResult, blockPosition, playerDigType.ordinal(),
          packet.deepClone(), playerLocation, InteractionType.BREAK,
          cancel
        )
      );
      if(cancel) {
        event.setCancelled(true);
      }
    } else {
      lowerViolation(player, InteractionType.BREAK);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.MONITOR, // last one to work with position
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK")
    }
  )
  public void receivePositionUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    World world = player.getWorld();
    User user = userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    InteractionMeta interactionMeta = metaOf(user);
    List<TraceReport> traceReportList = interactionMeta.traceReportList;

    if(traceReportList.isEmpty()) {
      return;
    }

    Location playerLocation = movementData.verifiedLocation();
    playerLocation.setYaw(movementData.rotationYaw);
    playerLocation.setPitch(movementData.rotationPitch);

    WrappedMovingObjectPosition raycastResult = Raytracer.blockRayTrace(player, playerLocation);
    boolean hitMiss = raycastResult == null || raycastResult.hitVec == WrappedVector.ZERO;
    WrappedBlockPosition raycastVector = hitMiss ? WrappedBlockPosition.ORIGIN : raycastResult.getBlockPos();
    Location raycastLocation = raycastVector.toLocation(world);

    for (TraceReport traceReport : traceReportList) {
      Location targetLocation = traceReport.targetBlock.toLocation(world);

      boolean invalid = hitMiss ||
          raycastLocation.distance(targetLocation) > 0 ||
          traceReport.targetDirection != raycastResult.sideHit.getIndex();

      InteractionType type = traceReport.type();
      boolean flagMessage = !type.bufferAvailable || interactionMeta.violationLevel.get(type) > 3;

      if(invalid && flagMessage) {
        if(type == InteractionType.BREAK) {
          String typeName = targetLocation.getBlock().getType().name().toLowerCase().replace("_", "").replace("block", "");
          String append = "";

          if (hitMiss || (raycastLocation.getBlockX() == 0 && raycastLocation.getBlockY() == 0 && raycastLocation.getBlockZ() == 0)) {
            append = " (looking in air)";
          } else if(raycastLocation.distance(targetLocation) > 0 && raycastLocation.getBlock().getType() != Material.AIR) {
            String blockName = raycastLocation.getBlock().getType().name().toLowerCase().replace("_", "").replace("block", "");

            if(raycastLocation.getBlock().getType() == targetLocation.getBlock().getType()) {
              blockName = "a different " + blockName;
            }
            append = " (looking at " + blockName + " block)";
          } else if (traceReport.targetDirection != raycastResult.sideHit.getIndex()){
            append = " (invalid block face)";
          }

          plugin.retributionService().markPlayer(player, 0, name(), "broke a " + typeName + " block out of sight" + append);
        } else if(type == InteractionType.PLACE) {
          String typeAgainstName = targetLocation.getBlock().getType().name().toLowerCase().replace("_", "").replace("block", "");
          String typeName = player.getItemInHand().getType().name().toLowerCase().replace("_", "").replace("block", "");

          plugin.retributionService().markPlayer(player, 0, name(), "tried to placed a " + typeName + " block against a "+typeAgainstName+" block out of sight");
        }
      }

      if(!invalid) {
        lowerViolation(player, type);
        lowerViolation(player, type);
      }

      ResponseType response = type.response();

      if(response == ResponseType.RAYTRACE_CAST) {
        if(hitMiss) {
          player.updateInventory();
          refreshBlock(player, targetLocation);
          for (WrappedEnumDirection direction : WrappedEnumDirection.values()) {
            Location placedBlock = targetLocation.clone().add(direction.getDirectionVec().convertToBukkitVec());
            refreshBlock(player, placedBlock);
          }
        } else if(traceReport.wasCanceled()) {
          PacketContainer packet = traceReport.thePacket();
          BlockPosition blockPosition1 = new BlockPosition(raycastLocation.getBlockX(), raycastLocation.getBlockY(), raycastLocation.getBlockZ());

          if(packet.getDirections().size() > 0) {
            packet.getDirections().write(0, raycastResult.sideHit.toDirection());
          } else {
            packet.getIntegers().write(0, raycastResult.sideHit.getIndex());
          }

          packet.getBlockPositionModifier().write(0, blockPosition1);
          refreshBlock(player, targetLocation);
          for (WrappedEnumDirection direction : WrappedEnumDirection.values()) {
            Location placedBlock = targetLocation.clone().add(direction.getDirectionVec().convertToBukkitVec());
            refreshBlock(player, placedBlock);
          }
          Synchronizer.synchronize(() -> receiveExcludedPacket(player, packet));
        }
      } else {
        if (!invalid && traceReport.wasCanceled()) {
          Synchronizer.synchronize(() -> receiveExcludedPacket(event.getPlayer(), traceReport.thePacket));
        } else {
          player.updateInventory();
          refreshBlock(player, targetLocation);
          for (WrappedEnumDirection direction : WrappedEnumDirection.values()) {
            Location placedBlock = targetLocation.clone().add(direction.getDirectionVec().convertToBukkitVec());
            refreshBlock(player, placedBlock);
          }
        }
      }
    }

    traceReportList.clear();
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

  private void receiveExcludedPacket(Player player, PacketContainer packet) {
    try {
      userOf(player).ignoreNextPacket();
      ProtocolLibrary.getProtocolManager().recieveClientPacket(player, packet);
    } catch (InvocationTargetException | IllegalAccessException exception) {
      exception.printStackTrace();
    }
  }

  public boolean processViolation(Player player, InteractionType type) {
    boolean cancel = false;
    InteractionMeta interactionMeta = metaOf(player);
    if(type.bufferAvailable) {
      int vl = interactionMeta.violationLevel.computeIfAbsent(type, x -> 0);
      vl = MathHelper.minmax(0, vl + 1,8);
//      player.sendMessage(type + ": " + vl);
      if(vl > 3) {
        cancel = true;
      }
      interactionMeta.violationLevel.put(type, vl);
    } else {
      cancel = true;
    }
    return cancel;
  }

  public void lowerViolation(Player player, InteractionType type) {
    if(!type.bufferAvailable) {
      return;
    }
    InteractionMeta interactionMeta = metaOf(player);
    int vl = interactionMeta.violationLevel.computeIfAbsent(type, x -> 0);
    vl = MathHelper.minmax(0, vl - 1,8);
    interactionMeta.violationLevel.put(type, vl);
  }

  public static class InteractionMeta extends UserCustomCheckMeta {
    final List<TraceReport> traceReportList = new ArrayList<>();
    final Map<InteractionType, Integer> violationLevel = Maps.newEnumMap(InteractionType.class);
  }

  public enum InteractionType {
    PLACE(ResponseType.RAYTRACE_CAST, true),
    BREAK(ResponseType.CANCEL, false),
    INTERACT(ResponseType.RAYTRACE_CAST, true);

    final ResponseType response;
    final boolean bufferAvailable;

    InteractionType(ResponseType response, boolean bufferAvailable) {
      this.response = response;
      this.bufferAvailable = bufferAvailable;
    }

    public ResponseType response() {
      return response;
    }
  }

  public enum ResponseType {
    RAYTRACE_CAST,
    CANCEL
  }

  public static class TraceReport {
    private final WrappedMovingObjectPosition raytraceResult;
    private final BlockPosition targetBlock;
    private final int targetDirection;
    private final PacketContainer thePacket;
    private final Location contextPosition;
    private final InteractionType type;
    private final boolean canceled;

    public TraceReport(
      WrappedMovingObjectPosition raytraceResult,
      BlockPosition targetBlock, int targetDirection, PacketContainer thePacket,
      Location contextPosition,
      InteractionType type,
      boolean canceled
    ) {
      this.raytraceResult = raytraceResult;
      this.targetBlock = targetBlock;
      this.targetDirection = targetDirection;
      this.thePacket = thePacket;
      this.contextPosition = contextPosition.clone();
      this.type = type;
      this.canceled = canceled;
    }

    public WrappedMovingObjectPosition raytraceResult() {
      return raytraceResult;
    }

    public BlockPosition targetBlock() {
      return targetBlock;
    }

    public int targetDirection() {
      return targetDirection;
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

    public boolean wasCanceled() {
      return canceled;
    }
  }
}
