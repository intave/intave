package de.jpx3.intave.check.world.breakspeedlimiter;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.BreakSpeedLimiter;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.module.violation.ViolationProcessor;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.packet.converter.BlockPositionConverter;
import de.jpx3.intave.reflect.access.ReflectiveEntityAccess;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class CompletionDurationCheck extends MetaCheckPart<BreakSpeedLimiter, CompletionDurationCheck.BreakSpeedFinishMeta> {
  public CompletionDurationCheck(BreakSpeedLimiter parentCheck) {
    super(parentCheck, BreakSpeedFinishMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      POSITION, POSITION_LOOK, LOOK, FLYING, VEHICLE_MOVE
    }
  )
  public void tickUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    BreakSpeedFinishMeta meta = metaOf(user);

    if (meta.balance > 0 && !event.isCancelled()) {
      meta.balance -= 0.005;
    }
  }

//  @PacketSubscription(
//    priority = ListenerPriority.LOW,
//    packetsIn = {
//      ARM_ANIMATION
//    }
//  )
//  public void actualTickUpdate(PacketEvent event) {
//    Player player = event.getPlayer();
//    User user = userOf(player);
//    BreakSpeedFinishMeta meta = metaOf(user);
//
//    ProtocolMetadata clientData = user.meta().protocol();
//    InventoryMetadata inventoryData = user.meta().inventory();
//
//    if (meta.breakProcess) {
//      ItemStack itemInHand = inventoryData.heldItem();
//      BlockPosition blockPosition = meta.targetBlockPosition;
//
//      float blockDamage = clientData.flyingPacketStream()
//        ? BlockInnerAccess.blockDamage(player, itemInHand, blockPosition)
//        : resolveBlockDamageOnGround(player, itemInHand, blockPosition);
//      player.sendMessage("block damage in tick: " + blockDamage);
//      meta.curBlockDamageMP += blockDamage;
//      meta.maximumBlockDamage = Math.max(meta.maximumBlockDamage, blockDamage);
//    }
//  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      BLOCK_DIG
    }
  )
  public void receiveBlockAction(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    BreakSpeedFinishMeta meta = metaOf(user);
//    ProtocolMetadata clientData = user.meta().protocol();
    InventoryMetadata inventoryData = user.meta().inventory();

    ItemStack heldItem = inventoryData.heldItem();
    PacketContainer packet = event.getPacket();
//    BlockPosition blockPosition = packet.getBlockPositionModifier().read(0);
    BlockPosition blockPosition = event.getPacket().getModifier()
      .withType(Lookup.serverClass("BlockPosition"), BlockPositionConverter.threadConverter())
      .read(0);
    EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().read(0);

    switch (digType) {
      case START_DESTROY_BLOCK: {
        float blockDamage = BlockInteractionAccess.blockDamage(player, heldItem, blockPosition);
        meta.breakProcess = true;
        meta.breakProcessStartTime = System.currentTimeMillis();
        meta.curBlockDamageMP = blockDamage;
        meta.targetBlockPosition = blockPosition;
        meta.maximumBlockDamage = blockDamage;
        break;
      }
      case STOP_DESTROY_BLOCK: {
//        if (clientData.flyingPacketStream()) {
//          float blockDamageDealt = BlockInnerAccess.blockDamage(player, heldItem, blockPosition);
//          if (blockDamageDealt < 0.79 && meta.balance++ >= 2) { // ~79%
//            String message = "broke block too quickly";
//            String percentage = (int)(blockDamageDealt * 100d) + "%";
//            String details = "at " + percentage;
//
//            ViolationProcessor violationProcessor = IntavePlugin.singletonInstance().violationProcessor();
//            Violation violation = Violation.builderFor(BreakSpeedLimiter.class)
//              .forPlayer(player).withMessage(message).withDetails(details)
//              .withVL(10).build();
//            ViolationContext violationContext = violationProcessor.processViolation(violation);
//            if (violationContext.shouldCounterThreat()) {
//              event.setCancelled(true);
//              refreshBlocksAround(player, blockPosition.toLocation(player.getWorld()));
//            }
//          }
//        } else {

        if (meta.targetBlockPosition != blockPosition) {
          blockPosition = meta.targetBlockPosition;
        }

        if (blockPosition == null) {
          return;
        }

        long requiredDuration = resolveMillisecondsOf(resolveBlockDamageOnGround(player, heldItem, blockPosition));
        long actualDuration = System.currentTimeMillis() - meta.breakProcessStartTime;
        long exceeded = Math.max(0, requiredDuration - actualDuration);

        if (exceeded > 100 && meta.balance++ >= 2) {
          String message = "broke block too quickly";
          String details = MathHelper.formatDouble(exceeded / 50d, 2) + " ticks faster than expected";
          ViolationProcessor violationProcessor = Modules.violationProcessor();
          Violation violation = Violation.builderFor(BreakSpeedLimiter.class)
            .forPlayer(player).withMessage(message).withDetails(details)
            .withVL(10).build();
          ViolationContext violationContext = violationProcessor.processViolation(violation);
          if (violationContext.shouldCounterThreat()) {
            event.setCancelled(true);
            refreshBlocksAround(player, blockPosition.toLocation(player.getWorld()));
          }
        }
//        }
//        break;
      }
      case ABORT_DESTROY_BLOCK:
        meta.breakProcessStartTime = System.currentTimeMillis();
        meta.curBlockDamageMP = 0f;
        meta.targetBlockPosition = null;
        meta.breakProcess = false;
        meta.maximumBlockDamage = Float.MIN_VALUE;
    }
  }

  private void refreshBlocksAround(Player player, Location targetLocation) {
    Synchronizer.synchronize(() -> {
      player.updateInventory();
      refreshBlock(player, targetLocation);
//      for (EnumDirection direction : EnumDirection.values()) {
//        Location placedBlock = targetLocation.clone().add(direction.getDirectionVec().convertToBukkitVec());
//        refreshBlock(player, placedBlock);
//      }
    });
  }

  private void refreshBlock(Player player, Location location) {
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_CHANGE);
    if (!VolatileBlockAccess.isInLoadedChunk(location.getWorld(), location.getBlockX(), location.getBlockZ())) {
      return;
    }
    Block block = VolatileBlockAccess.blockAccess(location);
    Object handle = BlockVariantNativeAccess.nativeVariantAccess(block);
    WrappedBlockData blockData = WrappedBlockData.fromHandle(handle);
    packet.getBlockData().write(0, blockData);
    BlockPosition position = new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    packet.getBlockPositionModifier().write(0, position);
    PacketSender.sendServerPacket(player, packet);
  }

  private long resolveMillisecondsOf(float blockDamage) {
    if (blockDamage == 0) {
      return 0;
    }
    long time = 0;
    float curBlockDamageMP = 0f;
    int iterationCountdown = 100;
    while (curBlockDamageMP < 1f) {
      curBlockDamageMP += blockDamage;
      time += 50;
      if (--iterationCountdown < 0) {
        break;
      }
    }
    return time;
  }

  private float resolveBlockDamageOnGround(
    Player player,
    ItemStack itemInHand,
    BlockPosition blockPosition
  ) {
    boolean onGroundBefore = ReflectiveEntityAccess.onGround(player);
    ReflectiveEntityAccess.setOnGround(player, true);
    float blockDamage = BlockInteractionAccess.blockDamage(player, itemInHand, blockPosition);
    ReflectiveEntityAccess.setOnGround(player, onGroundBefore);
    return blockDamage;
  }

  public static final class BreakSpeedFinishMeta extends CheckCustomMetadata {
    public BlockPosition targetBlockPosition;
    public float curBlockDamageMP = 0f;
    public float maximumBlockDamage;
    /*
    breakProcess won't be set to false if a player is stop breaking a block (only when destroying a block),
    So isBreakingBlock is a boolean which determines if the player is breaking a block and is false when he stops breaking.
     */
    public boolean breakProcess;
    public long breakProcessStartTime;
    public double balance;
  }
}