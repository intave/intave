package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.executor.IntaveThreadFactory;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaAttackData;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.entity.Player;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LinearRegressionHeuristic extends IntaveMetaCheckPart<Heuristics, LinearRegressionHeuristic.LinearRegressionHeuristicMeta> {
  private final ExecutorService executorService = Executors.newSingleThreadExecutor(IntaveThreadFactory.ofLowestPriority());

  public LinearRegressionHeuristic(Heuristics parentCheck) {
    super(parentCheck, LinearRegressionHeuristic.LinearRegressionHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "ENTITY_ACTION")
    }
  )
  public void sneakStart(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    LinearRegressionHeuristicMeta meta = metaOf(player);

    EnumWrappers.PlayerAction action = event.getPacket().getPlayerActions().read(0);

    if(action == EnumWrappers.PlayerAction.START_SNEAKING) {
      linearRegression(player, meta, true);
    }
  }


  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK")
    }
  )
  public void clientTickUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    LinearRegressionHeuristicMeta meta = metaOf(player);

    boolean updated = addValuesToList(user);

    if(updated) {
      linearRegression(player, meta, false);
    }
  }

  private boolean addValuesToList(User user) {
    Player player = user.player();
    UserMetaMovementData movementData = user.meta().movementData();
    LinearRegressionHeuristicMeta meta = metaOf(player);
    UserMetaAttackData attackData = user.meta().attackData();

    if (!attackData.recentlyAttacked(2000)) {
      return false;
    }

    float yawDiff = MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw);

    double x = MathHelper.distanceInDegrees(attackData.perfectYaw(), movementData.rotationYaw);
    double y = yawDiff;

    if(y > 1) {
      if(x > meta.width)
        meta.width = x;

      if(y > meta.height)
        meta.height = y;

//      player.sendMessage("" + x + " " + y);

      Vector vector = new Vector(x, y);
      meta.vectorList.add(vector);
      return true;
    }

    return false;
  }

  private void linearRegression(Player player, LinearRegressionHeuristicMeta meta, boolean showAsWindow) {
    double xSum = 0;
    double ySum = 0;

    for(Vector vector : meta.vectorList) {
      xSum += vector.x;
      ySum += vector.y;
    }

    double xAverage = xSum /  meta.vectorList.size();
    double yAverage = ySum /  meta.vectorList.size();

    double nummerator = 0;
    double denominator = 0;

    for(Vector vector :  meta.vectorList) {
      nummerator += (vector.x - xAverage) * (vector.y - yAverage);
      denominator += (vector.x - xAverage) * (vector.x - xAverage);
    }

    double m = nummerator / denominator;
    double b = yAverage -  m * xAverage;

    if(showAsWindow) {
      drawWindow(player, m, b);
    }

    player.sendMessage( b + " " + denominator);
  }

  private void drawWindow(Player player, double m, double b) {
    executorService.execute(()->{
      User user = userOf(player);
      LinearRegressionHeuristicMeta meta = metaOf(player);
      final int pointRadius = 2;
      final int width = 800;
      final int height = 800;

      JFrame window = new JFrame();
      window.setSize(width, height);
      window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

      JPanel panel = new JPanel() {
        @Override
        public void paint(Graphics g) {
          ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

          g.setColor(Color.blue);
          for (Vector vector : meta.vectorList) {
            int x = (int) map(vector.x, 0, meta.width, 0, width);
            int y = (int) map(vector.y, 0, meta.height, 0, height);

            g.fillOval(x - pointRadius, y - pointRadius, pointRadius * 2, pointRadius * 2);
          }

          double x1 = 0;
          double y1 = m * x1 + b;
          double x2 = meta.width;
          double y2 = m * x2 + b;

          ((Graphics2D) g).setStroke(new BasicStroke(4f));
          g.setColor(Color.black);

          x1 = map(x1, 0, meta.width, 0, width);
          y1 = map(y1, 0, meta.height, height, 0);
          x2 = map(x2, 0, meta.width, 0, width);
          y2 = map(y2, 0, meta.height, height, 0);

          g.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
        }
      };
      window.add(panel);
      window.setVisible(true);
    });
  }

  private double map(double from, double minFrom, double maxFrom, double minTo, double maxTo) {
    return from / (Math.abs(minFrom) + Math.abs(maxFrom)) * (Math.abs(minTo) + Math.abs(maxTo));
  }

  public static class LinearRegressionHeuristicMeta extends UserCustomCheckMeta {
    List<Vector> vectorList = new ArrayList<>();
    double width;
    double height;
  }
}

class Vector {
  double x;
  double y;

  public Vector(double x, double y) {
    this.x = x;
    this.y = y;
  }
}