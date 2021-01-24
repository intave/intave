package de.jpx3.intave.detect.checks.world;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.common.collect.Maps;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.CheckViolationLevelDecrementer;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedBlockPosition;
import de.jpx3.intave.tools.wrapper.WrappedEnumDirection;
import de.jpx3.intave.tools.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.BlockAccessor;
import de.jpx3.intave.world.block.BlockDataAccess;
import de.jpx3.intave.world.collision.BoundingBoxAccess;
import de.jpx3.intave.world.collision.Collision;
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK;

public final class InteractionRaytrace extends IntaveMetaCheck<InteractionRaytrace.InteractionMeta> {
  private final IntavePlugin plugin;

  private final CheckViolationLevelDecrementer decrementer;

  public InteractionRaytrace(IntavePlugin plugin) {
    super("InteractionRaytrace", "interactionraytrace", InteractionMeta.class);
    this.plugin = plugin;
    this.decrementer = new CheckViolationLevelDecrementer(this, 1);
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
    User user = userOf(player);
    InteractionMeta interactionMeta = metaOf(user);
    UserMetaMovementData movementData = user.meta().movementData();
    PacketContainer packet = event.getPacket();
    BlockPosition blockPosition = packet.getBlockPositionModifier().readSafely(0);
    if (blockPosition == null || movementData.inVehicle()) {
      return;
    }
    Integer enumDirection = packet.getIntegers().readSafely(0);
    if (enumDirection == null) {
      enumDirection = packet.getDirections().readSafely(0).ordinal();
    }
    if (enumDirection == 255) {
      return;
    }

    if (event.isCancelled()) {
      return;
    }

    Vector facing = null;
    StructureModifier<Float> floatStructureModifier = packet.getFloat();
    if (floatStructureModifier.size() == 3) {
      facing = new Vector();
      facing.setX(floatStructureModifier.read(0));
      facing.setY(floatStructureModifier.read(1));
      facing.setZ(floatStructureModifier.read(2));
    }

    Location playerLocation = movementData.verifiedLocation().clone();
    playerLocation.setYaw(movementData.rotationYaw);
    playerLocation.setPitch(movementData.rotationPitch);

    Material clickedType = BlockAccessor.blockAccess(blockPosition.toLocation(player.getWorld())).getType();
    boolean clickable = BlockDataAccess.isClickable(clickedType);
    Material itemTypeInHand = user.meta().inventoryData().heldItemType();
    boolean isPlacement = itemTypeInHand != Material.AIR && itemTypeInHand.isBlock() && !clickable;

    Interaction interaction = new Interaction(
      player.getWorld(), player, blockPosition, enumDirection, packet.deepClone(),
      isPlacement ? InteractionType.PLACE : InteractionType.INTERACT, facing
    );
    interactionMeta.interactionList.add(interaction);
//    if(!isPlacement) {
//    }
    event.setCancelled(true);
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
    User user = userOf(player);

    if (blockPosition == null) {
      return;
    }

    if (event.isCancelled()) {
      return;
    }

    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    ItemStack heldItemStack = inventoryData.heldItem();
    if (InventoryUseItemHelper.isSwordItem(player, heldItemStack) && player.getGameMode() == GameMode.CREATIVE) {
      event.setCancelled(true);
      return;
    }

    EnumWrappers.PlayerDigType playerDigType = packet.getPlayerDigTypes().readSafely(0);
    float blockDamage = BlockDataAccess.blockDamage(player, user.meta().inventoryData().heldItem(), blockPosition);
    boolean instantBreak = blockDamage == Float.POSITIVE_INFINITY || blockDamage >= 1.0f;
    boolean breakBlock = instantBreak || playerDigType == STOP_DESTROY_BLOCK;

    if (!breakBlock) {
      return;
    }

    EnumWrappers.Direction direction = packet.getDirections().readSafely(0);
    int enumDirection = direction == null ? 255 : direction.ordinal();
    boolean blocking = blockPosition.getX() == 0 && blockPosition.getY() == 0 &&  blockPosition.getZ() == 0 && enumDirection == 0;

    if (enumDirection == 255 || blocking) {
      return;
    }

    InteractionMeta interactionMeta = metaOf(user);

    Interaction interaction = new Interaction(
      player.getWorld(), player, blockPosition, enumDirection, packet.deepClone(), InteractionType.BREAK,
      null
    );
    interactionMeta.interactionList.add(interaction);
    event.setCancelled(true);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST, // last one to work with position
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING"),
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
    List<Interaction> interactionList = interactionMeta.interactionList;

    if (interactionList.isEmpty()) {
      return;
    }

    Location playerLocation = new Location(world, movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);
    playerLocation.setYaw(movementData.rotationYaw);
    playerLocation.setPitch(movementData.rotationPitch);

    Location playerLocationmdf = playerLocation.clone();
    playerLocationmdf.setYaw(movementData.lastRotationYaw);

    WrappedMovingObjectPosition raycastResult = Raytracer.blockRayTrace(player, playerLocation);
    WrappedMovingObjectPosition raycastResultmdf = Raytracer.blockRayTrace(player, playerLocationmdf);

    for (Interaction interaction : interactionList) {
      processTraceReport(interaction, raycastResult, raycastResultmdf, false);
    }
    interactionList.clear();
  }

  private void processTraceReport(
    Interaction interaction,
    WrappedMovingObjectPosition raycastResult,
    WrappedMovingObjectPosition raycastResultmdf,
    boolean delayed
  ) {
    if (interaction.entered()) {
      return;
    }
    interaction.enter();

    World world = interaction.world();
    Player player = interaction.player();
    User user = UserRepository.userOf(player);
    InteractionMeta interactionMeta = metaOf(player);
    UserMetaMovementData movementData = user.meta().movementData();

    boolean estimateMouseDelayFix = interactionMeta.estimateMouseDelayFix;

    // first raytrace check
    WrappedMovingObjectPosition firstRaytraceResult = estimateMouseDelayFix ? raycastResultmdf : raycastResult;
    boolean hitMiss = (firstRaytraceResult == null || firstRaytraceResult.hitVec == WrappedVector.ZERO);
    WrappedBlockPosition raycastVector = hitMiss ? WrappedBlockPosition.ORIGIN : firstRaytraceResult.getBlockPos();
    Location raycastLocation = raycastVector.toLocation(world);
    Location targetLocation = interaction.targetBlock.toLocation(world);
    boolean invalidFacing = interaction.facingVector() != null &&
      firstRaytraceResult != null &&
      validateFacingVector(user, interaction.targetBlock, firstRaytraceResult.hitVec, interaction.facingVector(), interaction.type());
    boolean invalid = hitMiss ||
      raycastLocation.distance(targetLocation) > 0 ||
      interaction.targetDirection != firstRaytraceResult.sideHit.getIndex() ||
      invalidFacing;

    // if first raytrace failed..
    if (invalid) {
      // ..try again with mouse delay fix toggled differently
      WrappedMovingObjectPosition secondRaytraceResult = estimateMouseDelayFix ? raycastResult : raycastResultmdf;
      boolean hitMiss2 = secondRaytraceResult == null || secondRaytraceResult.hitVec == WrappedVector.ZERO;
      WrappedBlockPosition raycastVector2 = hitMiss2 ? WrappedBlockPosition.ORIGIN : secondRaytraceResult.getBlockPos();
      Location raycastLocation2 = raycastVector2.toLocation(world);
      invalidFacing = interaction.facingVector() != null &&
        secondRaytraceResult != null &&
        validateFacingVector(user, interaction.targetBlock, secondRaytraceResult.hitVec, interaction.facingVector(), interaction.type());
      invalid = hitMiss2 ||
        raycastLocation2.distance(targetLocation) > 0 ||
        interaction.targetDirection != secondRaytraceResult.sideHit.getIndex() ||
        invalidFacing;

      interactionMeta.estimateMouseDelayFix = invalid == interactionMeta.estimateMouseDelayFix;
    }

    if (!invalid) {
      decrementer.decrement(user, 0.25);
    }

    boolean flag = invalid && performFlag(interaction, raycastResult, targetLocation, raycastLocation, hitMiss, invalidFacing);
    emulatePacket(interaction, raycastResult, targetLocation, raycastLocation, hitMiss, flag, invalidFacing);
  }

  private boolean validateFacingVector(User user, BlockPosition blockPosition, WrappedVector hitVec, Vector sent, InteractionType interactionType) {
    float f = (float) (hitVec.xCoord - (double) blockPosition.getX());
    float f1 = (float) (hitVec.yCoord - (double) blockPosition.getY());
    float f2 = (float) (hitVec.zCoord - (double) blockPosition.getZ());

    f = (int) (f * 16.0F) / 16.0F;
    f1 = (int) (f1 * 16.0F) / 16.0F;
    f2 = (int) (f2 * 16.0F) / 16.0F;

    double tolerance = 0;
    switch (interactionType) {
      case BREAK:
        tolerance = 0;
        break;
      case INTERACT:
        tolerance = 0.0625;
        break;
      case PLACE:
        tolerance = 10;
    }

    double distance = sent.distance(new Vector(f, f1, f2));
    return distance > tolerance;
  }

  private void emulatePacket(
    Interaction interaction,
    WrappedMovingObjectPosition raycastResult,
    Location targetLocation,
    Location raycastLocation,
    boolean hitMiss,
    boolean punishment,
    boolean invalidFacing
  ) {
    Player player = interaction.player();
    User user = userOf(player);
    ResponseType response = interaction.type().response();
    BoundingBoxAccess boundingBoxAccess = user.boundingBoxAccess();
    if (invalidFacing) {
      response = ResponseType.CANCEL;
    }
    boolean canRefreshBlocks = interaction.type != InteractionType.INTERACT;
    if (response == ResponseType.RAYTRACE_CAST) {
      if (hitMiss || raycastResult == null) {
//        player.sendMessage("Emulation " + raycastLocation + " " + targetLocation);
        if (canRefreshBlocks) {
          refreshBlocksAround(player, targetLocation);
        }
        boundingBoxAccess.invalidateOverride(interaction.world, targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());
      } else {
        // check if player collides with placement location
        if (canRefreshBlocks) {
          World world = player.getWorld();
          Material material = user.meta().inventoryData().heldItemType();
          int dat = 0;
          boolean replace = BlockDataAccess.replacementPlace(world, new BlockPosition(raycastLocation.toVector()));
          Location placementLocation = replace ? raycastLocation : raycastLocation.clone().add(raycastResult.sideHit.getDirectionVec().convertToBukkitVec());
          boolean raytraceCollidesWithPosition = Collision.playerInImaginaryBlock(
            user, world, placementLocation.getBlockX(), placementLocation.getBlockY(), placementLocation.getBlockZ(),
            material.getId(),
            dat
          );
          if (raytraceCollidesWithPosition) {
            refreshBlocksAround(player, targetLocation);
            boundingBoxAccess.invalidateOverride(interaction.world, targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());
            return;
          }
        }
        PacketContainer packet = interaction.thePacket();
        if (punishment) {
          if (packet.getDirections().size() > 0) {
            packet.getDirections().write(0, raycastResult.sideHit.toDirection());
          } else {
            packet.getIntegers().write(0, raycastResult.sideHit.getIndex());
          }
          packet.getBlockPositionModifier().write(
            0,
            new BlockPosition(raycastLocation.getBlockX(), raycastLocation.getBlockY(), raycastLocation.getBlockZ())
          );
          boundingBoxAccess.invalidate(targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());
          boundingBoxAccess.invalidateOverride(interaction.world, targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());
        } else {
          boundingBoxAccess.invalidate(targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());
        }
        receiveExcludedPacket(player, packet);
        if (canRefreshBlocks) {
          Synchronizer.synchronize(() -> refreshBlocksAround(player, targetLocation));
        }
      }
    } else {
      if (punishment) {
        if (canRefreshBlocks) {
          refreshBlocksAround(player, targetLocation);
        }
        boundingBoxAccess.invalidateOverride(interaction.world, targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());
      } else {
        receiveExcludedPacket(player, interaction.thePacket);
      }
    }
  }

  private void refreshBlocksAround(Player player, Location targetLocation) {
    player.updateInventory();
    refreshBlock(player, targetLocation);
    for (WrappedEnumDirection direction : WrappedEnumDirection.values()) {
      Location placedBlock = targetLocation.clone().add(direction.getDirectionVec().convertToBukkitVec());
      refreshBlock(player, placedBlock);
    }
  }

  private boolean performFlag(
    Interaction interaction,
    WrappedMovingObjectPosition raycastResult,
    Location targetLocation,
    Location raycastLocation,
    boolean hitMiss,
    boolean invalidFacing
  ) {
    Player player = interaction.player();
    User user = userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    InteractionType type = interaction.type();

    Block targetLocationBlock = BlockAccessor.blockAccess(targetLocation);
    Block raycastLocationBlock = BlockAccessor.blockAccess(raycastLocation);
    if (targetLocationBlock.getType() == Material.AIR || raycastLocationBlock.getType() == Material.AIR) {
      return true;
    }

    double vl = 0;
    boolean mustFlag = false;

    String message, details;
    if (type == InteractionType.BREAK) {
      String typeName = shortenTypeName(targetLocationBlock.getType());
      String append = "";
      if (hitMiss || (raycastLocation.getBlockX() == 0 && raycastLocation.getBlockY() == 0 && raycastLocation.getBlockZ() == 0)) {
        append = "looking in air";
        vl = 5;
      } else if (raycastLocation.distance(targetLocation) > 0 && raycastLocationBlock.getType() != Material.AIR) {
        String blockName = shortenTypeName(raycastLocationBlock.getType());
        if (raycastLocationBlock.getType() == targetLocationBlock.getType()) {
          blockName = "a different " + blockName;
        }
        append = "looking at " + blockName + " block";
        vl = 5;
      } else if (interaction.targetDirection != raycastResult.sideHit.getIndex()) {
        append = "invalid block face";
        vl = 15;
      }

      message = "performed invalid break";// +" -" + append;
      details = typeName + " block, " + append;
    } else if (type == InteractionType.PLACE) {
      String typeAgainstName = shortenTypeName(targetLocationBlock.getType());
      String typeName = shortenTypeName(user.meta().inventoryData().heldItemType());

      String append = "";
      if (hitMiss || (raycastLocation.getBlockX() == 0 && raycastLocation.getBlockY() == 0 && raycastLocation.getBlockZ() == 0)) {
        append = "looking in air";
        vl = 2.5;
      } else if (raycastLocation.distance(targetLocation) > 0 && raycastLocationBlock.getType() != Material.AIR) {
        String blockName = shortenTypeName(raycastLocationBlock.getType());
        if (raycastLocationBlock.getType() == targetLocationBlock.getType()) {
          blockName = "a different " + blockName;
        }
        append = "looking at " + blockName + " block";
        vl = 5;
      } else if (interaction.targetDirection != raycastResult.sideHit.getIndex()) {
        append = "invalid block face";
        vl = 2.5;
      }

      message = "performed invalid placement";
      details = typeName + " block on " + typeAgainstName + " block, " + append;
    } else {
      String typeAgainstName = shortenTypeName(targetLocationBlock.getType());
      message = "invalid interaction";
      details = typeAgainstName + " block";
      mustFlag = true;
      vl = 0;
//      return true;
    }

//    if(invalidFacing) {
//      vl = 0;
//    }

    return plugin.retributionService().processViolation(player, vl, name(), message, details) || mustFlag;
  }

  private String shortenTypeName(Material type) {
    return type.name().toLowerCase().replace("_", "").replace("block", "");
  }

  private void refreshBlock(Player player, Location location) {
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_CHANGE);
    Block block = BlockAccessor.blockAccess(location);
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

/*  public boolean processViolation(Player player, InteractionType type) {
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
  }*/

  public static class InteractionMeta extends UserCustomCheckMeta {
    final List<Interaction> interactionList = new ArrayList<>();
    final Map<InteractionType, Integer> violationLevel = Maps.newEnumMap(InteractionType.class);

    public long lastPlacement;
    public boolean estimateMouseDelayFix = false;
  }

  public enum InteractionType {
    PLACE(ResponseType.RAYTRACE_CAST, false),
    BREAK(ResponseType.CANCEL, false),
    INTERACT(ResponseType.RAYTRACE_CAST, false);

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

  public static class Interaction {
    private final World world;
    private final Player player;
    private final BlockPosition targetBlock;
    private final int targetDirection;
    private final PacketContainer thePacket;
    private final InteractionType type;
    private final Vector facingVector;
    private boolean entered = false;

    public Interaction(
      World world, Player player,
      BlockPosition targetBlock,
      int targetDirection,
      PacketContainer thePacket,
      InteractionType type,
      Vector facingVector) {
      this.world = world;
      this.player = player;
      this.targetBlock = targetBlock;
      this.targetDirection = targetDirection;
      this.thePacket = thePacket;
      this.type = type;
      this.facingVector = facingVector;
    }

    public PacketContainer thePacket() {
      return thePacket;
    }

    public InteractionType type() {
      return type;
    }

    public Vector facingVector() {
      return facingVector;
    }

    public World world() {
      return world;
    }

    public Player player() {
      return player;
    }

    public void enter() {
      entered = true;
    }

    public boolean entered() {
      return entered;
    }
  }
}
