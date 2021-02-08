package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.BlockAccessor;
import de.jpx3.intave.world.block.BlockDataAccess;
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;

public class AirClickLimitHeuristic extends IntaveMetaCheckPart<Heuristics, AirClickLimitHeuristic.AirClickLimitHeuristicMeta> {

  public AirClickLimitHeuristic(Heuristics parentCheck) {
    super(parentCheck, AirClickLimitHeuristic.AirClickLimitHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY")
    }
  )
  public void entityHit(PacketEvent event) {
    if (ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.COMBAT_UPDATE)) {
      return;
    }

    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    EnumWrappers.EntityUseAction entityUseAction = event.getPacket().getEntityUseActions().read(0);

    if(entityUseAction == EnumWrappers.EntityUseAction.ATTACK) {
      meta.resetedLeftClickCounterThisTick = true;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE")
    }
  )
  public void blockPlace(PacketEvent event) {
    if (ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.COMBAT_UPDATE)) {
      return;
    }

    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);
    UserMetaInventoryData inventoryData = user.meta().inventoryData();

    // TODO: 01/28/21 Warning by Richy: The block-place is empty for native server versions from 1.9! Use the USE_ITEM packet instead
    BlockPosition blockPosition = event.getPacket().getBlockPositionModifier().read(0);

    if(blockPosition != null) {
      if (blockPosition.getX() != -1 && blockPosition.getY() != -1 && blockPosition.getZ() != -1 && inventoryData.heldItem() != null) {
        meta.resetedLeftClickCounterThisTick = true;
      } else {
        Material clickedType = BlockAccessor.blockAccess(blockPosition.toLocation(player.getWorld())).getType();
        boolean clickable = BlockDataAccess.isClickable(clickedType);

        if(clickable) {
          meta.resetedLeftClickCounterThisTick = true;
        }
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_DIG")
    }
  )
  public void blockDig(PacketEvent event) {
    if (ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.COMBAT_UPDATE)) {
      return;
    }

    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    EnumWrappers.PlayerDigType digType = event.getPacket().getPlayerDigTypes().read(0);

    if(digType == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK) {
      meta.isBreakingClientSide = true;

      BlockPosition blockPosition = event.getPacket().getBlockPositionModifier().read(0);
      meta.currentDiggedBlock = blockPosition;
    } else if(digType == EnumWrappers.PlayerDigType.ABORT_DESTROY_BLOCK) {
      meta.isBreakingClientSide = false;
    } else if(digType == EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK) {
      meta.currentDiggedBlock = null;
      meta.isBreakingClientSide = false;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK")
    }
  )
  public void clientTickUpdate(PacketEvent event) {
    if (ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.COMBAT_UPDATE)) {
      return;
    }

    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    if(meta.isBreakingClientSide) {
      meta.resetedLeftClickCounterThisTick = true;
    }

    if(meta.swingsThisTick > 0 && !meta.resetedLeftClickCounterThisTick) {
      /*TODO: Überprüfen ob der Spieler im letztem Tick auch ein Swing-packet gesendet hat
         oder er ein Stop-break Packet im Tick davor gesendet hat. (Um so wenig Raytracing wie
         möglich zu machen)

         Gibt auch ein Minecraft Bug bei dem man nach dem man ein Block abgebaut hat für 5 Ticks noch Swing-packets sendet.
      **/
      World world = event.getPlayer().getWorld();
      UserMetaMovementData movementData = user.meta().movementData();

      Location playerLocation = new Location(world, movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);
      playerLocation.setYaw(movementData.rotationYaw);
      playerLocation.setPitch(movementData.rotationPitch);
      WrappedMovingObjectPosition raycastResult = Raytracer.blockRayTrace(player, playerLocation);
      if(raycastResult != null && raycastResult.hitVec != WrappedVector.ZERO) {
        // TODO: check if meta.lastDiggedBlock is the same as from the raycastResult

//        player.sendMessage("Is digging client side but not server side");
        meta.resetedLeftClickCounterThisTick = true;
        if(meta.currentDiggedBlock != null) {
          Synchronizer.synchronize(() -> sendStopDig(player, meta));
        }
      }
    }

    if(!meta.resetedLeftClickCounterThisTick) {
      meta.tickArray[meta.tickIndex] = meta.swingsThisTick;
    }

    int sum = 0;
    for(int clickOfTick : meta.tickArray) {
      sum += clickOfTick;
    }

//    if(sum != 0)
//      player.sendMessage("cps: " + sum);

    if(sum > 13 && user.meta().clientData().protocolVersion() <= UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE) {
      parentCheck().saveAnomaly(player,
        Anomaly.anomalyOf(
          "11",
          sum > 14 ? Confidence.VERY_LIKELY : Confidence.PROBABLE,
          Anomaly.Type.AUTOCLICKER,
          "too many swing packets in air " + sum, Anomaly.AnomalyOption.DELAY_128s
        ));
    }

    prepareNextTick(meta);
  }

  private void prepareNextTick(AirClickLimitHeuristicMeta meta) {
    meta.resetedLeftClickCounterThisTick = false;
    meta.swingsThisTick = 0;

    meta.tickIndex++;
    if(meta.tickIndex > 19) {
      meta.tickIndex = 0;
    }

    meta.tickArray[meta.tickIndex] = 0;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "ARM_ANIMATION")
    }
  )
  public void swing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    meta.swingsThisTick++;
  }

  private void sendStopDig(Player player, AirClickLimitHeuristicMeta meta) {
    try {
      PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Client.BLOCK_DIG);

      packet.getBlockPositionModifier().write(0, meta.currentDiggedBlock);
      packet.getDirections().write(0, EnumWrappers.Direction.DOWN);
      packet.getPlayerDigTypes().write(0, EnumWrappers.PlayerDigType.ABORT_DESTROY_BLOCK);

      userOf(player).ignoreNextPacket();
      ProtocolLibrary.getProtocolManager().recieveClientPacket(player, packet);
    } catch (InvocationTargetException | IllegalAccessException exception) {
      exception.printStackTrace();
    }
  }

  public static class AirClickLimitHeuristicMeta extends UserCustomCheckMeta {
    BlockPosition currentDiggedBlock;
    private int tickIndex;
    private int[] tickArray = new int[20];
    private int swingsThisTick = 0;

    private boolean isBreakingClientSide;
    private boolean isBreakingServerSide;
    private boolean resetedLeftClickCounterThisTick;
  }
}