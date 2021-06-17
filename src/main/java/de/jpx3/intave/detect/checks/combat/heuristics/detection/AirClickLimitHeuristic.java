package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.violation.AttackNerfStrategy;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import static de.jpx3.intave.event.packet.PacketId.Client.*;

public class AirClickLimitHeuristic extends IntaveMetaCheckPart<Heuristics, AirClickLimitHeuristic.AirClickLimitHeuristicMeta> {

  public AirClickLimitHeuristic(Heuristics parentCheck) {
    super(parentCheck, AirClickLimitHeuristic.AirClickLimitHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void entityHit(PacketEvent event) {
    if (ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0)) {
      return;
    }

    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    EnumWrappers.EntityUseAction entityUseAction = event.getPacket().getEntityUseActions().read(0);

    if (entityUseAction == EnumWrappers.EntityUseAction.ATTACK) {
      meta.resetedLeftClickCounterThisTick = true;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      BLOCK_PLACE
    }
  )
  public void blockPlace(PacketEvent event) {
    if (ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0)) {
      return;
    }

    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    // TODO: 01/28/21 Warning by Richy: The block-place is empty for native server versions from 1.9! Use the USE_ITEM packet instead
    BlockPosition blockPosition = event.getPacket().getBlockPositionModifier().read(0);
    int blockPlaceDirection = event.getPacket().getIntegers().read(0);

    if (blockPosition != null) {
      if (blockPlaceDirection != 255) {
        Material clickedType = BukkitBlockAccess.blockAccess(blockPosition.toLocation(player.getWorld())).getType();
        boolean clickable = BlockDataAccess.isClickable(clickedType);

        if (clickable) {
          meta.resetedLeftClickCounterThisTick = true;
        }
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      BLOCK_DIG
    }
  )
  public void blockDig(PacketEvent event) {
    if (ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0)) {
      return;
    }

    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    EnumWrappers.PlayerDigType digType = event.getPacket().getPlayerDigTypes().read(0);

    if (digType == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK) {
      meta.isBreakingClientSide = true;

      meta.currentDiggedBlock = event.getPacket().getBlockPositionModifier().read(0);
    } else if (digType == EnumWrappers.PlayerDigType.ABORT_DESTROY_BLOCK) {
      meta.isBreakingClientSide = false;
    } else if (digType == EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK) {
      meta.currentDiggedBlock = null;
      meta.isBreakingClientSide = false;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void clientTickUpdate(PacketEvent event) {
    if (ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0)) {
      return;
    }

    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    if (meta.isBreakingClientSide) {
      meta.resetedLeftClickCounterThisTick = true;
    }

    if (meta.swingsThisTick > 0 && !meta.resetedLeftClickCounterThisTick) {
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

      boolean error = false;
      WrappedMovingObjectPosition raycastResult;
      try {
        raycastResult = Raytracer.blockRayTrace(player, playerLocation);
      } catch (Exception exception) {
//        exception.printStackTrace();
        raycastResult = null;
        error = true;
      }

      if (error || (raycastResult != null && raycastResult.hitVec != WrappedVector.ZERO)) {
        // TODO: check if meta.lastDiggedBlock is the same as from the raycastResult (geht nur wenn man den mc bug fixt der für 5 ticks flaggt wenn man ein block abgebaut hat)

//        player.sendMessage("Is digging client side but not server side");
        meta.resetedLeftClickCounterThisTick = true;
        Synchronizer.synchronize(() -> {
          if (meta.currentDiggedBlock != null) {
            sendStopDig(player, meta);
          }
        });
      }
    }

    if (!meta.resetedLeftClickCounterThisTick) {
      meta.tickArray[meta.tickIndex] = meta.swingsThisTick;
    }

    int sum = 0;
    for (int clickOfTick : meta.tickArray) {
      sum += clickOfTick;
    }

    if (sum != 0) {
//      player.sendMessage("cps: " + sum);
    }

    if (sum > 13 && user.meta().clientData().protocolVersion() <= UserMetaClientData.VER_1_8) {
      meta.flaggCounter++;
      double timeDiffrenceInSeconds = (System.currentTimeMillis() - meta.lastFlagTimeStamp) / 1000d;

      if (sum > meta.maxCPS) {
        meta.maxCPS = sum;
      }

      if (timeDiffrenceInSeconds > 30) {
        Confidence confidence;
        if (meta.flaggCounter > 10 && meta.maxCPS > 15) {
          confidence = Confidence.LIKELY;
        } else {
          confidence = Confidence.PROBABLE;
        }

        Anomaly anomaly = Anomaly.anomalyOf("11",
          IntaveControl.DISABLE_AUTOCLICKER_CHECK ? Confidence.NONE : confidence,
          Anomaly.Type.AUTOCLICKER,
          "swings in air (cps " + meta.maxCPS + ") (sum " + meta.flaggCounter + ")", Anomaly.AnomalyOption.DELAY_128s
        );
        parentCheck().saveAnomaly(player, anomaly);

        if (meta.flaggCounter > 20 && meta.maxCPS > 18) {
          //dmc27
          user.applyAttackNerfer(AttackNerfStrategy.GARBAGE_HITS, "27");
          user.applyAttackNerfer(AttackNerfStrategy.BLOCKING, "27");
        }

        meta.lastFlagTimeStamp = System.currentTimeMillis();
        meta.maxCPS = 0;
        meta.flaggCounter = 0;
      }
    } else {
      if (meta.flaggCounter == 0) {
        meta.lastFlagTimeStamp = System.currentTimeMillis();
      }
    }

    prepareNextTick(meta);
  }

  private void prepareNextTick(AirClickLimitHeuristicMeta meta) {
    meta.resetedLeftClickCounterThisTick = false;
    meta.swingsThisTick = 0;

    meta.tickIndex++;
    if (meta.tickIndex > 19) {
      meta.tickIndex = 0;
    }

    meta.tickArray[meta.tickIndex] = 0;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void swing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    meta.swingsThisTick++;
  }

  private void sendStopDig(Player player, AirClickLimitHeuristicMeta meta) {
    //    Synchronizer.synchronize(()->{
    //        try {
    //          PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_BREAK_ANIMATION);
    //          packet.getIntegers().write(0, player.getEntityId());
    //          packet.getBlockPositionModifier().write(0, meta.currentDiggedBlock);
    //          packet.getIntegers().write(1, 0);
    ////          userOf(player).ignoreNextPacket();
    //          ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    //        } catch(Exception e) {
    //          e.printStackTrace();
    //        }
    //    });
    //TODO: das BLOCK_DIG verhindert nicht komplett das der block abgebaut wird (das abbauen wird manchmal vom server verhindert wenn der spieler den block zu schnell abgebaut hat)
//    try {
//      PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Client.BLOCK_DIG);
//
//      packet.getBlockPositionModifier().write(0, meta.currentDiggedBlock);
//      packet.getDirections().write(0, EnumWrappers.Direction.DOWN);
//      packet.getPlayerDigTypes().write(0, EnumWrappers.PlayerDigType.ABORT_DESTROY_BLOCK);
//
//      userOf(player).ignoreNextPacket();
//      ProtocolLibrary.getProtocolManager().recieveClientPacket(player, packet);
//    } catch (InvocationTargetException | IllegalAccessException exception) {
//      exception.printStackTrace();
//    }
  }

  public static class AirClickLimitHeuristicMeta extends UserCustomCheckMeta {
    private int maxCPS;
    private long lastFlagTimeStamp;
    private int flaggCounter;

    BlockPosition currentDiggedBlock;
    private int tickIndex;
    private int[] tickArray = new int[20];
    private int swingsThisTick = 0;

    private boolean isBreakingClientSide;
    private boolean resetedLeftClickCounterThisTick;
  }
}