package de.jpx3.intave.check.combat.heuristics.detect.clickpatterns;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.PacketEventsAdapter;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.MovingObjectPosition;
import de.jpx3.intave.share.NativeVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class OldAirClickLimitHeuristic extends MetaCheckPart<Heuristics, OldAirClickLimitHeuristic.AirClickLimitHeuristicMeta> {

  public OldAirClickLimitHeuristic(Heuristics parentCheck) {
    super(parentCheck, OldAirClickLimitHeuristic.AirClickLimitHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void entityHit(ProtocolPacketEvent event, WrapperPlayClientInteractEntity packet) {
    if (PacketEventsAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0)) {
      return;
    }

    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
      meta.resetedLeftClickCounterThisTick = true;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      BLOCK_PLACE
    }
  )
  public void blockPlace(ProtocolPacketEvent event, WrapperPlayClientPlayerBlockPlacement packet) {
    if (PacketEventsAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0)) {
      return;
    }

    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    // TODO: 01/28/21 Warning by Richy: The block-place is empty for native server versions from 1.9! Use the USE_ITEM packet instead
    BlockPosition blockPosition = blockPositionOf(packet.getBlockPosition());
    int blockPlaceDirection = packet.getFaceId();

    if (blockPosition != null) {
      if (blockPlaceDirection != 255) {
        Material clickedType = VolatileBlockAccess.typeAccess(user, blockPosition.toLocation(player.getWorld()));
        boolean clickable = BlockInteractionAccess.isClickable(clickedType);

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
  public void blockDig(ProtocolPacketEvent event, WrapperPlayClientPlayerDigging packet) {
    if (PacketEventsAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0)) {
      return;
    }

    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    DiggingAction digType = packet.getAction();

    if (digType == DiggingAction.START_DIGGING) {
      meta.isBreakingClientSide = true;

      meta.currentDiggedBlock = blockPositionOf(packet.getBlockPosition());
    } else if (digType == DiggingAction.CANCELLED_DIGGING) {
      meta.isBreakingClientSide = false;
    } else if (digType == DiggingAction.FINISHED_DIGGING) {
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
  public void clientTickUpdate(ProtocolPacketEvent event) {
    if (PacketEventsAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0)) {
      return;
    }

    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    if (meta.isBreakingClientSide) {
      meta.resetedLeftClickCounterThisTick = true;
    }

    if (meta.swingsThisTick > 0 && !meta.resetedLeftClickCounterThisTick) {
      /*TODO: Check whether the player has also sent a swing packet in the last tick
         or if he has sent a stop-break packet in the tick before. (To do as little raytracing as
         possible)

         There is also a Minecraft bug where you send swing packets for 5 ticks after you have dismantled a block.
      **/
      World world = player.getWorld();
      MovementMetadata movementData = user.meta().movement();

      Location playerLocation = new Location(world, movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);
      playerLocation.setYaw(movementData.rotationYaw);
      playerLocation.setPitch(movementData.rotationPitch);

      boolean error = false;
      MovingObjectPosition raycastResult;
      try {
        raycastResult = Raytracing.blockRayTrace(player, playerLocation);
      } catch (Exception exception) {
//        exception.printStackTrace();
        raycastResult = null;
        error = true;
      }

      if (error || (raycastResult != null && raycastResult.hitVec != NativeVector.ZERO)) {
        // TODO: check if meta.lastDiggedBlock is the same as from the raycastResult (only works if you fix the mc bug that flags for 5 ticks when you have dismantled a block)

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

    if (sum > 13 && user.protocolVersion() <= ProtocolMetadata.VER_1_8) {
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

        Anomaly anomaly = Anomaly.anomalyOf("191",
          IntaveControl.DISABLE_AUTOCLICKER_CHECK ? Confidence.NONE : confidence,
          Anomaly.Type.AUTOCLICKER,
          "swings in air (cps " + meta.maxCPS + ") (sum " + meta.flaggCounter + ")", Anomaly.AnomalyOption.DELAY_128s
        );
        parentCheck().saveAnomaly(player, anomaly);

        if (meta.flaggCounter > 20/* && !IntaveControl.DISABLE_AUTOCLICKER_CHECK*/) {
          //dmc27
//          user.nerf(AttackNerfStrategy.GARBAGE_HITS, "27");
//          user.nerf(AttackNerfStrategy.DMG_LIGHT, "27");
//          user.nerf(AttackNerfStrategy.BLOCKING, "27");
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

  private BlockPosition blockPositionOf(Vector3i vector) {
    return new BlockPosition(vector.x, vector.y, vector.z);
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
  public void swing(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    AirClickLimitHeuristicMeta meta = metaOf(user);

    meta.swingsThisTick++;
  }

  private void sendStopDig(Player player, AirClickLimitHeuristicMeta meta) {
  }

  public static class AirClickLimitHeuristicMeta extends CheckCustomMetadata {
    private int maxCPS;
    private long lastFlagTimeStamp;
    private int flaggCounter;

    BlockPosition currentDiggedBlock;
    private int tickIndex;
    private final int[] tickArray = new int[20];
    private int swingsThisTick = 0;

    private boolean isBreakingClientSide;
    private boolean resetedLeftClickCounterThisTick;
  }
}
