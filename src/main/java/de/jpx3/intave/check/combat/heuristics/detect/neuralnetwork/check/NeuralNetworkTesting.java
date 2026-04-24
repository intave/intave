package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.check;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.executor.IntaveThreadFactory;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.share.ClientMath;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public class NeuralNetworkTesting extends MetaCheckPart<Heuristics, NeuralNetworkTesting.NeuralNetworkTestingMeta> {
  // for selftests without needing to run intave on a server

  private final String testUsername = "DarkAndBlue";

  public NeuralNetworkTesting(Heuristics parentCheck) {
    super(parentCheck, NeuralNetworkTesting.NeuralNetworkTestingMeta.class);
  }

  public static class NeuralNetworkTestingMeta extends CheckCustomMetadata {
    public int lastAttack;
  }

  private static CopyOnWriteArrayList<Point> redPoints = new CopyOnWriteArrayList<>();
  private static CopyOnWriteArrayList<Point> greenPoints = new CopyOnWriteArrayList<>();

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void playerAttack(ProtocolPacketEvent event, WrapperPlayClientInteractEntity packet) {
    User user = userOf(event.getPlayer());
    NeuralNetworkTestingMeta meta = metaOf(user);
    if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
      meta.lastAttack = 0;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION,
      POSITION_LOOK,
      FLYING,
      LOOK,
    }
  )
  public void playerMove(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    NeuralNetworkTestingMeta meta = metaOf(player);

    Entity target = user.meta().attack().lastAttackedEntity();
    if (target == null) {
      return;
    }
    MovementMetadata movementData = user.meta().movement();
    //user.player().sendMessage(String.format("x=%.3f y=%.3f z=%.3f",
    //  target.position.posX, target.position.posY, target.position.posZ));
    float lastPlayerYaw = ClientMath.wrapAngleTo180_float(movementData.lastRotationYaw);
    float playerYaw = ClientMath.wrapAngleTo180_float(movementData.rotationYaw);
    float serverYaw = resolveYawRotation(target.position, movementData.lastPositionX, movementData.lastPositionZ);

    float expectedYawDelta = (serverYaw - lastPlayerYaw) % 360f;
    float yawDelta = (playerYaw - lastPlayerYaw) % 360f;
    //player.sendMessage(String.format("%.4f %.4f", expectedYawDelta, yawDelta));
    if (meta.lastAttack <= 0 && yawDelta > 0) {
      double x = Scene.mapData(expectedYawDelta, -45, 45, -1, 1);
      double y = Scene.mapData(yawDelta, -45, 45, -1, 1);
      Point point = new Point(x, y);
      addPoint(player, point);
    }
  }

  private float resolveYawRotation(Entity.EntityPositionContext entityPositions, double posX, double posZ) {
    double diffX = entityPositions.posX - posX;
    double diffZ = entityPositions.posZ - posZ;
    return (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsIn = {
      POSITION,
      POSITION_LOOK,
      FLYING,
      LOOK,
    }
  )
  public void playerMoveEnd(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    NeuralNetworkTestingMeta neuralNetworkTestingMeta = metaOf(player);
    neuralNetworkTestingMeta.lastAttack++;
  }

  void addPoint(Player player, Point point) {
    if (player.getName().contains(testUsername)) {
      greenPoints.add(point);
    } else {
      redPoints.add(point);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      ENTITY_ACTION_IN
    }
  )
  public void playerSneaking(ProtocolPacketEvent event, WrapperPlayClientEntityAction packet) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movement = user.meta().movement();

    if (packet.getAction() == WrapperPlayClientEntityAction.Action.START_SNEAKING && player.getName().contains(testUsername)) {
      double motion = Math.hypot(movement.motionX(), movement.motionZ());
      if (motion < 0.01) {
        openWindow();
      }
    }
  }

  static void openWindow() {
    redPoints = new CopyOnWriteArrayList<>();
    greenPoints = new CopyOnWriteArrayList<>();

    // opening a new thread to let old JFrames open
    ExecutorService executorService = Executors.newSingleThreadExecutor(IntaveThreadFactory.ofLowestPriority());
    executorService.execute(() -> {
      new Window(redPoints, greenPoints);
    });
  }
}
