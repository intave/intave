package de.jpx3.intave.check.combat.heuristics.detect.unused;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.executor.IntaveThreadFactory;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.converter.PlayerAction;
import de.jpx3.intave.packet.converter.PlayerActionResolver;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class LinearRegressionHeuristic extends MetaCheckPart<Heuristics, LinearRegressionHeuristic.LinearRegressionHeuristicMeta> {
  private final ExecutorService executorService = Executors.newSingleThreadExecutor(IntaveThreadFactory.ofLowestPriority());

  public LinearRegressionHeuristic(Heuristics parentCheck) {
    super(parentCheck, LinearRegressionHeuristic.LinearRegressionHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ENTITY_ACTION_IN
    }
  )
  public void sneakStart(PacketEvent event) {
    Player player = event.getPlayer();
    PlayerAction action = PlayerActionResolver.resolveActionFromPacket(event.getPacket());

    if (action.isStartSneak()) {
      createNewWindow(player);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsIn = {
      POSITION_LOOK, POSITION, FLYING, LOOK
    }
  )
  public void clientTickUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    LinearRegressionHeuristicMeta meta = metaOf(player);

    addValuesToList(user);
    meta.lastMoveTimeStamp = System.currentTimeMillis();
  }

  private void addValuesToList(User user) {
    Player player = user.player();
    LinearRegressionHeuristicMeta meta = metaOf(player);

    if (meta.lastMoveTimeStamp == 0)
      meta.lastMoveTimeStamp = System.currentTimeMillis();

    double timeDiff = (double) (System.currentTimeMillis() - meta.lastMoveTimeStamp);

    Vector2d vector = new Vector2d(meta.addedCounter, timeDiff);
    meta.addedCounter++;
    meta.vectorList.add(vector);

//    linearRegression(player, meta);

    if (meta.panel != null) {
      meta.panel.repaint();
    }

    if (meta.vectorList.size() > 200)
      meta.vectorList.remove(0);
  }

//  private void linearRegression(Player player, LinearRegressionHeuristicMeta meta) {
//    double xSum = 0;
//    double ySum = 0;
//
//    for (Vector vector : meta.vectorList) {
//      xSum += vector.x;
//      ySum += vector.y;
//    }
//
//    double xAverage = xSum / meta.vectorList.size();
//    double yAverage = ySum / meta.vectorList.size();
//
//    double nummerator = 0;
//    double denominator = 0;
//
//    for (Vector vector : meta.vectorList) {
//      nummerator += (vector.x - xAverage) * (vector.y - yAverage);
//      denominator += (vector.x - xAverage) * (vector.x - xAverage);
//    }
//
//    double m = nummerator / denominator;
//    double b = yAverage - m * xAverage;
//
//    meta.m = m;
//    meta.b = b;
//  }

  private void createNewWindow(Player player) {
    executorService.execute(() -> {
      LinearRegressionHeuristicMeta meta = metaOf(player);

      JFrame window = new JFrame();
      window.setSize(800, 800);
      window.setFocusable(true);
      window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

      JPanel panel = new JPanel() {
        @Override
        public void paint(Graphics g) {
          super.paint(g);
          StringBuilder newTitle = new StringBuilder("Value count: " + meta.vectorList.size());
          for (int i = newTitle.length(); i < 30; i++)
            newTitle.append(" ");
          window.setTitle(newTitle.toString());

          Graphics2D g2d = ((Graphics2D) g);
          g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

          g.setColor(Color.orange);
//          for (Vector vector : meta.vectorList) {
//            int x = (int) map(vector.x, 0, meta.highestVectorX, 0, getWidth());
//            int y = (int) map(vector.y, 0, meta.highestVectorY, 0, getHeight());
//
//            g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
//          }
          g2d.setStroke(new BasicStroke(1f));
          g.setColor(Color.yellow);

          int minVectorX = Integer.MAX_VALUE;
          int minVectorY = Integer.MAX_VALUE;
          int maxVectorX = Integer.MIN_VALUE;
          int maxVectorY = Integer.MIN_VALUE;

          for (Vector2d vector : meta.vectorList) {
            if (vector.x > maxVectorX)
              maxVectorX = (int) vector.x;
            if (vector.y > maxVectorY)
              maxVectorY = (int) vector.y;
            if (vector.x < minVectorX)
              minVectorX = (int) vector.x;
            if (vector.y < minVectorY)
              minVectorY = (int) vector.y;
          }

//          System.out.println("" + minVectorX + " " + maxVectorX);

          int lastVectorX = Integer.MAX_VALUE;
          int lastVectorY = Integer.MAX_VALUE;

          for (Vector2d vector : meta.vectorList) {
            int vectorX = (int) map2(vector.x, minVectorX, maxVectorX, 0, getWidth());
            int vectorY = (int) map2(vector.y, minVectorY, maxVectorY, 0, getHeight());

            if (lastVectorX != Integer.MAX_VALUE && lastVectorY != Integer.MAX_VALUE) {
              g.drawLine(lastVectorX, lastVectorY, vectorX, vectorY);
            }

            lastVectorX = vectorX;
            lastVectorY = vectorY;
          }

//          double x1 = 0;
//          double y1 = meta.m * x1 + meta.b;
//          double x2 = meta.highestVectorX;
//          double y2 = meta.m * x2 + meta.b;

//          x1 = map(x1, 0, meta.highestVectorX, 0, getWidth());
//          y1 = map(y1, 0, meta.highestVectorY, getHeight(), 0);
//          x2 = map(x2, 0, meta.highestVectorX, 0, getWidth());
//          y2 = map(y2, 0, meta.highestVectorY, getHeight(), 0);

//          g.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
        }
      };
      panel.setBackground(new Color(51, 51, 51));
      panel.setLocation(20, 20);

      JPanel outerPanel = new JPanel() {
        {
          setLayout(null);

          addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent ev) {
              panel.setSize(getWidth() - 40, getHeight() - 40);
            }
          });

          panel.invalidate();
        }

        @Override
        public void paint(Graphics g) {
          super.paint(g);
        }
      };

      outerPanel.add(panel);
      meta.panel = outerPanel;

      window.add(outerPanel);
      window.setVisible(true);
    });
  }

  private static double map2(double from, double minFrom, double maxFrom, double minTo, double maxTo) {
    double heightFrom = maxFrom - minFrom;
    double fromWithoutMinFrom = from - minFrom;
    double fromConvertedToHeight = fromWithoutMinFrom / heightFrom;

    double heightTo = maxTo - minTo;
    double toWithoutMinTo = fromConvertedToHeight * heightTo;
    double to = toWithoutMinTo + minTo;

    return to;
  }

  private static double map(double from, double minFrom, double maxFrom, double minTo, double maxTo) {
    return from / (Math.abs(minFrom) + Math.abs(maxFrom)) * (Math.abs(minTo) + Math.abs(maxTo));
  }

  public static class LinearRegressionHeuristicMeta extends CheckCustomMetadata {
    public JPanel panel;
    //    public double b;
//    public double m;
    List<Vector2d> vectorList = new CopyOnWriteArrayList<>();
    long lastMoveTimeStamp = 0;
    public int addedCounter;
  }

  static class Vector2d {
    double x;
    double y;

    public Vector2d(double x, double y) {
      this.x = x;
      this.y = y;
    }
  }
}
