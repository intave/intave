package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.executor.IntaveThreadFactory;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.shade.ClientMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import javax.swing.*;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions.ActivationFunction.*;

public class NeuralNetworkTesting extends MetaCheckPart<Heuristics, NeuralNetworkTesting.NeuralNetworkTestingMeta> {
  private final ExecutorService executorService = Executors.newSingleThreadExecutor(IntaveThreadFactory.ofLowestPriority());
  
  public NeuralNetworkTesting(Heuristics parentCheck) {
    super(parentCheck, NeuralNetworkTesting.NeuralNetworkTestingMeta.class);
  }
  
  public static class NeuralNetworkTestingMeta extends CheckCustomMetadata {
    public int lastAttackCounter;
  }
  
  private static final NeuralNetwork NEURAL_NETWORK = new NeuralNetwork(
    2,
    sigmoid,
    20,
    sigmoid,
    1
  );
  private static final List<Point> redPoints = new CopyOnWriteArrayList<>();
  private static final List<Point> greenPoints = new CopyOnWriteArrayList<>();
  private static JFrame currentOpenWindow;
  
  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void playerAttack(PacketEvent event) {
    Player player = event.getPlayer();
    NeuralNetworkTestingMeta neuralNetworkTestingMeta = metaOf(player);
    neuralNetworkTestingMeta.lastAttackCounter = 0;
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
    NeuralNetworkTestingMeta neuralNetworkTestingMeta = metaOf(player);
    
    AttackMetadata attack = user.meta().attack();
    MovementMetadata movement = user.meta().movement();
    
//    double a = ClientMathHelper.wrapAngleTo180_double(attack.perfectYaw() - movement.rotationYaw);
//    double b = ClientMathHelper.wrapAngleTo180_double(attack.previousPerfectYaw() - movement.lastRotationYaw);
    
    double a = ClientMathHelper.wrapAngleTo180_double(attack.previousPerfectYaw() - movement.lastRotationYaw);
    double b = ClientMathHelper.wrapAngleTo180_double(attack.perfectYaw() - movement.rotationYaw);

//    player.sendMessage(
//      "pefYaw " + MathHelper.formatDouble(attack.perfectYaw(), 4)
//      + " yaw " + MathHelper.formatDouble(movement.rotationYaw, 4)
//        + " yawDelta " + MathHelper.formatDouble(yawDelta, 4)
//    );
    
    if (attack.perfectYaw() != 0 && a != 0 && attack.recentlyAttacked(500)) {
      double x = mapData(a, -45, 45, -1, 1);
      double y = mapData(b, -45, 45, -1, 1);
      
      Point point = new Point(x, y);
      addPoint(player, point);
    }
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
    neuralNetworkTestingMeta.lastAttackCounter++;
  }
  
  void addPoint(Player player, Point point) {
    if (player.getName().contains("TheDarkBlue")) {
      greenPoints.add(point);
    } else {
      redPoints.add(point);
    }
  }
  
  double mapData(double value, double min, double max, double minTo, double maxTo) {
    return (1 - ((value - min) / (max - min))) * minTo + ((value - min) / (max - min)) * maxTo;
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
    
    if (playerActions != null && player.isOp()) {
      if (playerActions == EnumWrappers.PlayerAction.START_SNEAKING && player.getName().contains("Dark")) {
        double motion = Math.hypot(movement.motionX(), movement.motionZ());
        if (motion < 0.005) {
          openWindow();
        }
      }
    }
  }
  
  void openWindow() {
    if (currentOpenWindow == null) {
      executorService.execute(() -> {
        JFrame frame = new JFrame();
        currentOpenWindow = frame;
        frame.setSize(800, 800);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        Scene scene = new Scene();
        frame.add(scene);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            currentOpenWindow = null;
          }
        });
        while (true) {
          try {
            Thread.sleep(32);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          scene.repaint();
        }
      });
    } else {
      currentOpenWindow.setIgnoreRepaint(true);
      redPoints.clear();
      greenPoints.clear();
    }
  }
  
  class Scene extends JPanel {
    private final static int radius = 2;
    private final static int secondRadius = 3;
    
    public void paint(Graphics graphics) {
      BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
      int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
      
      for (int x = 0; x < image.getWidth(); x++) {
        for (int y = 0; y < image.getHeight(); y++) {
          int index = x + y * image.getHeight();
          
          double[] inputs = new double[] {
            mapData(x, 0, image.getWidth(), -1, 1),
            mapData(y, 0, image.getHeight(), -1, 1),
          };
          double result = NEURAL_NETWORK.predict(inputs).data[0][0];
          int brightness = (int) (Math.min(Math.max(result, 0), 1) * 255d);
          pixels[index] = new Color(brightness, brightness, brightness).getRGB();
        }
      }
      graphics.drawImage(image, 0, 0, getWidth(), getHeight(), null);
      
      ((Graphics2D) graphics).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      for (Point point : greenPoints) {
        drawPoint(graphics, point, secondRadius, Color.black);
        drawPoint(graphics, point, radius, Color.green);
      }
      
      for (Point point : redPoints) {
        drawPoint(graphics, point, secondRadius, Color.black);
        drawPoint(graphics, point, radius, Color.red);
      }
    }
    
    void drawPoint(Graphics graphics, Point point, int radius, Color color) {
      graphics.setColor(color);
      int x = (int) mapData(point.x, -1, 1, 0, getWidth());
      int y = (int) mapData(point.y, -1, 1, 0, getHeight());
      graphics.fillOval(x - radius, y - radius, radius * 2, radius * 2);
    }
  }
  
  class Point extends JPanel {
    double x, y;
    
    public Point(double x, double y) {
      this.x = x;
      this.y = y;
    }
  }
}