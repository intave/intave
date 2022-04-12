package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.check;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.NeuralNetwork;
import de.jpx3.intave.executor.IntaveThreadFactory;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.EntityShade;
import de.jpx3.intave.shade.ClientMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions.ActivationFunction.*;

public class NeuralNetworkTesting extends MetaCheckPart<Heuristics, NeuralNetworkTesting.NeuralNetworkTestingMeta> {
  private final String testUsername = "DarkAndBlue";
  
  public NeuralNetworkTesting(Heuristics parentCheck) {
    super(parentCheck, NeuralNetworkTesting.NeuralNetworkTestingMeta.class);
    NEURAL_NETWORK.localLearningRate = 0.03;
  }
  
  public static class NeuralNetworkTestingMeta extends CheckCustomMetadata {
    public int lastAttack;
  }
  
  private static final NeuralNetwork NEURAL_NETWORK = new NeuralNetwork(
    2,
    sigmoid,
    20,
    sigmoid,
    1
  );
  private static CopyOnWriteArrayList<Point> redPoints = new CopyOnWriteArrayList<>();
  private static CopyOnWriteArrayList<Point> greenPoints = new CopyOnWriteArrayList<>();
  
  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void playerAttack(PacketEvent event) {
    User user = userOf(event.getPlayer());
    NeuralNetworkTestingMeta meta = metaOf(user);
    PacketContainer packet = event.getPacket();
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
    if (action == null) {
      action = packet.getEnumEntityUseActions().read(0).getAction();
    }
    if (action == EnumWrappers.EntityUseAction.ATTACK) {
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
  public void playerMove(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    NeuralNetworkTestingMeta meta = metaOf(player);
    
    EntityShade target = user.meta().attack().lastAttackedEntity();
    if (target == null) {
      return;
    }
    MovementMetadata movementData = user.meta().movement();
    //user.player().sendMessage(String.format("x=%.3f y=%.3f z=%.3f",
    //  target.position.posX, target.position.posY, target.position.posZ));
    float lastPlayerYaw = ClientMathHelper.wrapAngleTo180_float(movementData.lastRotationYaw);
    float playerYaw = ClientMathHelper.wrapAngleTo180_float(movementData.rotationYaw);
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
  
  private float resolveYawRotation(EntityShade.EntityPositionContext entityPositions, double posX, double posZ) {
    final double diffX = entityPositions.posX - posX;
    final double diffZ = entityPositions.posZ - posZ;
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
  public void playerMoveEnd(PacketEvent event) {
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
      ENTITY_ACTION
    }
  )
  public void playerSneaking(PacketEvent event) {
    EnumWrappers.PlayerAction playerActions = event.getPacket().getPlayerActions().readSafely(0);
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movement = user.meta().movement();
    
    if (playerActions != null) {
      if (playerActions == EnumWrappers.PlayerAction.START_SNEAKING && player.getName().contains(testUsername)) {
        double motion = Math.hypot(movement.motionX(), movement.motionZ());
        if (motion < 0.01) {
          openWindow();
        }
      }
    }
  }
  
  void openWindow() {
    redPoints = new CopyOnWriteArrayList<>();
    greenPoints = new CopyOnWriteArrayList<>();
    
    // opening a new thread to let old JFrames open
    ExecutorService executorService = Executors.newSingleThreadExecutor(IntaveThreadFactory.ofLowestPriority());
    executorService.execute(() -> {
      new Window(NEURAL_NETWORK, redPoints, greenPoints);
    });
  }
}