package de.jpx3.intave.detect.checks.combat.heuristics;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.Location;
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
    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    EnumWrappers.EntityUseAction entityUseAction = event.getPacket().getEntityUseActions().read(0);

    if(entityUseAction == EnumWrappers.EntityUseAction.ATTACK) {
      meta.attackedThisTick = true;
    }
  }


  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE")
    }
  )
  public void blockPlace(PacketEvent event) {
    if(event.isCancelled()) {
      return;
    }
    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    // TODO: 01/28/21 Warning by Richy: The block-place is empty for native server versions from 1.9! Use the USE_ITEM packet instead
    BlockPosition blockPosition = event.getPacket().getBlockPositionModifier().read(0);

    if(blockPosition.getX() != -1 && blockPosition.getY() != -1 && blockPosition.getZ() != -1) {
      meta.blockPlacedThisTick = true;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_DIG")
    }
  )
  public void blockDig(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    EnumWrappers.PlayerDigType digType = event.getPacket().getPlayerDigTypes().read(0);

    if(digType == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK) {
      meta.currentDiggedBlock = event.getPacket().getBlockPositionModifier().read(0);

      meta.startBreakThisTick = true;
      meta.isBreakingClientSide = true;
    } else {
      meta.stopBreakThisTick = true;
      meta.isBreakingClientSide = false;
      meta.isBreakingServerSide = false;
    }

    if(digType == EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK) {
      meta.currentDiggedBlock = null;
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
    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    if(meta.getRealClicksPerTick() == 0) {
      meta.isBreakingServerSide = false;
      meta.isBreakingClientSide = false;
    }

//    if(meta.startBreakThisTick && meta.getRealClicksPerTick() != 2 && !meta.stopBreakThisTick) {
//      World world = event.getPlayer().getWorld();
//      UserMetaMovementData movementData = user.meta().movementData();
//
//      Location playerLocation = new Location(world,
//        movementData.positionX,
//        movementData.positionY,
//        movementData.positionZ);
//
//      playerLocation.setYaw(movementData.lastRotationYaw);
//      playerLocation.setPitch(movementData.lastRotationPitch);
//
//      WrappedMovingObjectPosition raycastResult = Raytracer.blockRayTrace(player, playerLocation);
//      if (raycastResult != null && raycastResult.hitVec != WrappedVector.ZERO) {
//      }else{
//        player.sendMessage("noweeeee " + raycastResult);
//      }
//
////      player.sendMessage("missing swing packet on start break block ");
////      parentCheck().saveAnomaly(player,
////        Anomaly.anomalyOf(
////          Confidence.VERY_LIKELY,
////          Anomaly.Type.AUTOCLICKER,
////          "missing swing packet on start break block", Anomaly.AnomalyOption.DELAY_128s | Anomaly.AnomalyOption.LIMIT_1
////        ));
//    }

    if(meta.currentDiggedBlock != null && !meta.isBreakingClientSide && meta.getRealClicksPerTick() > 0 && meta.getRealClicksOfLastTick() == 0) {
      World world = event.getPlayer().getWorld();
      UserMetaMovementData movementData = user.meta().movementData();

      Location playerLocation = new Location(world, movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);
      playerLocation.setYaw(movementData.rotationYaw);
      playerLocation.setPitch(movementData.rotationPitch);
      WrappedMovingObjectPosition raycastResult = Raytracer.blockRayTrace(player, playerLocation);
      if(raycastResult != null && raycastResult.hitVec != WrappedVector.ZERO) {
        // TODO: check if meta.lastDiggedBlock is the same as from the raycastResult

//        player.sendMessage("Is digging client side but not server side");
        meta.isBreakingServerSide = true;
        sendStopDig(player, meta);
      }
    }

    if(meta.attackedThisTick) {
      meta.removeClickFromTickArray();
    }

    if(meta.blockPlacedThisTick) {
      meta.removeClickFromTickArray();
    }

    if(meta.startBreakThisTick && !meta.stopBreakThisTick) {
      meta.removeClickFromTickArray();
    }

    if(meta.isBreakingClientSide || meta.isBreakingServerSide) {
      meta.removeClickFromTickArray();
    }

    int sum = 0;
    for(int clickOfTick : meta.tickArray) {
      sum += clickOfTick;
    }

//    player.sendMessage("cps: " + sum + " " + meta.startBreakThisTick);

    if(sum > 13 && user.meta().clientData().protocolVersion() <= UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE) {
      parentCheck().saveAnomaly(player,
        Anomaly.anomalyOf(
          sum > 14 ? Confidence.VERY_LIKELY : Confidence.MAYBE,
          Anomaly.Type.AUTOCLICKER,
          "too many swing packets in air " + sum, Anomaly.AnomalyOption.DELAY_128s
        ));
    }

    prepareNextTick(meta);
  }

  private void prepareNextTick(AirClickLimitHeuristicMeta meta) {
    meta.nextTick();

    meta.startBreakThisTick = false;
    meta.stopBreakThisTick = false;
    meta.blockPlacedThisTick = false;
    meta.attackedThisTick = false;
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

    meta.addClickToTick();
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
    public boolean startBreakThisTick;

    public boolean stopBreakThisTick;
    public boolean blockPlacedThisTick;
    public boolean attackedThisTick;
    boolean isBreakingClientSide;
    boolean isBreakingServerSide;

    private int tickIndex = 0;
    private int lastIndex;
    private int[] realTickArray = new int[20];
    private int[] tickArray = new int[20];
    BlockPosition currentDiggedBlock;

    private void addClickToTick() {
      realTickArray[tickIndex]++;
      tickArray[tickIndex]++;
    }

    private void removeClickFromTickArray() {
      tickArray[tickIndex]--;
    }

    private int getRealClicksPerTick() {
      return realTickArray[tickIndex];
    }

    private int getRealClicksOfLastTick() {
      return realTickArray[lastIndex];
    }

    private void nextTick() {
      lastIndex = tickIndex;
      tickIndex++;
      if(tickIndex > 19) {
        tickIndex = 0;
      }

      realTickArray[tickIndex] = 0;
      tickArray[tickIndex] = 0;
    }
  }
}