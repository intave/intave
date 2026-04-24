package de.jpx3.intave.check.other.inventoryclickanalysis;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.InventoryClickAnalysis;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.math.Matrix;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.module.violation.ViolationProcessor;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.CLOSE_WINDOW;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.WINDOW_CLICK;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public class RegrDelayAnalyzer extends MetaCheckPart<InventoryClickAnalysis, RegrDelayAnalyzer.ClickDelayMeta> {
  public RegrDelayAnalyzer(InventoryClickAnalysis parentCheck) {
    super(parentCheck, ClickDelayMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      PacketId.Server.OPEN_WINDOW
    }
  )
  public void openWindowPacket(Player player) {
    User user = userOf(player);
    ClickDelayMeta meta = metaOf(user);
    user.tickFeedback(() -> {
      meta.lastWindowOpenTimestamp = System.currentTimeMillis();
      meta.lastClickedTimestamp = System.currentTimeMillis();
      meta.firstClickTimestamp = 0;
      if (!meta.windowOpen) {
        meta.lastSlot = 22;
      }
      meta.windowOpen = true;
    });
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      WINDOW_CLICK
    }
  )
  public void windowClickPacket(Player player, WrapperPlayClientClickWindow windowClick) {
    if (player.getGameMode().equals(GameMode.CREATIVE)) {
      return;
    }
    User user = userOf(player);
    ClickDelayMeta meta = metaOf(user);
    int slot = windowClick.getSlot();
    ItemStack itemStack = windowClick.getCarriedItemStack() == null
      ? null
      : SpigotConversionUtil.toBukkitItemStack(windowClick.getCarriedItemStack());
    Material clickedItemType = itemStack == null ? Material.AIR : itemStack.getType();
    WrapperPlayClientClickWindow.WindowClickType clickType = windowClick.getWindowClickType();
    boolean isDrop = clickType == WrapperPlayClientClickWindow.WindowClickType.THROW && slot != -999;
    if (slot != -999 && meta.lastSlot != -999) {
      if ((clickedItemType != meta.lastClickedItemType || isDrop || missingItemStack(clickType)) && clickedItemType != Material.AIR && meta.lastClickedTimestamp != 0) {
        if (clickType != WrapperPlayClientClickWindow.WindowClickType.SWAP &&
          clickType != WrapperPlayClientClickWindow.WindowClickType.QUICK_CRAFT) {
          checkWindowClick(player, meta, slot);
        }
      }
    }
    meta.lastSlot = slot;
    meta.lastClickedItemType = clickedItemType;
    meta.lastClickedTimestamp = System.currentTimeMillis();
    if (meta.firstClickTimestamp == 0) {
      meta.firstClickTimestamp = System.currentTimeMillis();
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      CLOSE_WINDOW
    }
  )
  public void closeWindowPacket(Player player, WrapperPlayClientCloseWindow closePacket) throws Exception {
    User user = userOf(player);
    ClickDelayMeta meta = metaOf(user);

    meta.windowOpen = false;
//    player.sendMessage("Click history: " + meta.clickHistory.size() + " entries");

    if (meta.distanceTime.isEmpty() || meta.slotTime.isEmpty()) {
      return;
    }

//    double[][] movement = slotsMovement(user);
    // x move, y move, x velocity, y velocity
    // send message
//    for (double[] doubles : movement) {
//      player.sendMessage("x: " + formatDouble(doubles[0], 2) + " y: " + formatDouble(doubles[1], 2) + " vx: " + formatDouble(doubles[2], 2) + " vy: " + formatDouble(doubles[3], 2));
//    }

    if (meta.distanceTime.size() > 30) {
      List<double[]> clickHistory = meta.distanceTime;
      double sum = 0;
      for (double[] doubles : clickHistory) {
        sum += doubles[0] / doubles[1];
      }
      double averageSpeed = sum / clickHistory.size();
      double[] slope = slopeOfClicks(user);
      double pearson = pearsonCorrelation(user);
      double[] variances = clickTimeDistanceVariance(user);

      double meanDistance = variances[0];
      double meanTime = variances[1];
      double varDistance = variances[2];
      double varTime = variances[3];
      double covar = variances[4];

      if (meanTime > 2 && varTime < 0.01) {
        Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
          .forPlayer(player)
          .withVL(1)
          .withMessage("is taking items at constant speed")
          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
          .withDetails("time: " + formatDouble(meanTime, 2) + " mean, " + formatDouble(varTime, 2) + " var")
          .build();
        ViolationProcessor violationProcessor = Modules.violationProcessor();
        ViolationContext context = violationProcessor.processViolation(violation);
        if (context.violationLevelAfter() > 100) {
          user.nerf(AttackNerfStrategy.DMG_MEDIUM, "item speed");
        }
      }

      if (slope[0] > 0.3 && averageSpeed > 15 && varDistance > 0.1) {
        Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
          .forPlayer(player)
          .withVL(10)
          .withMessage("is taking items too quickly")
          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
          .withDetails("obeys " + formatDouble(slope[0], 2) + " d/t correlation at " + formatDouble(averageSpeed, 2) + " s/s")
          .build();
        ViolationProcessor violationProcessor = Modules.violationProcessor();
        ViolationContext context = violationProcessor.processViolation(violation);
        if (context.violationLevelAfter() > 100) {
          user.nerf(AttackNerfStrategy.DMG_MEDIUM, "item speed");
        }
      }

      if ((varDistance > 2 && varTime < 0.02) || (varDistance < 0.02 && varTime > 2)) {
        Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
          .forPlayer(player)
          .withVL(10)
          .withMessage("seems indifferent to distance taking items")
          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
          .withDetails("variance mismatch: " + formatDouble(varDistance, 2) + " d, " + formatDouble(varTime, 2) + " t")
          .build();
        ViolationProcessor violationProcessor = Modules.violationProcessor();
        ViolationContext context = violationProcessor.processViolation(violation);
        if (context.violationLevelAfter() > 100) {
          user.nerf(AttackNerfStrategy.DMG_MEDIUM, "item speed");
        }
      }

//      player.sendMessage(ChatColor.YELLOW + "CI | "+(meta.firstClickTimestamp-meta.lastWindowOpenTimestamp)+"ms fc | " + MathHelper.formatDouble(averageSpeed, 2) + " s/s avg" + " | " + formatDouble(slope[0], 2) + " distance/time correlation");
//      player.sendMessage(ChatColor.YELLOW + Arrays.toString(variances));

      if ((slope[0] < 0.1 || pearson < 0.1) && meanDistance > 1.2) {
        Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
          .forPlayer(player)
          .withVL(10)
          .withMessage("seems indifferent to distance taking items")
          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
          .withDetails(formatDouble(slope[0], 2) + " d/t, " + formatDouble(pearson, 2) + " pear, " + formatDouble(meanDistance, 2) + " mean d, " + formatDouble(varDistance, 2) + " var d")
          .build();
        ViolationProcessor violationProcessor = Modules.violationProcessor();
        ViolationContext context = violationProcessor.processViolation(violation);
//        System.out.println("seems indifferent to distance taking items" + formatDouble(slope[0], 2) + " d/t, " + formatDouble(pearson, 2) + " pear");

        if (context.violationLevelAfter() > 100) {
          user.nerf(AttackNerfStrategy.DMG_MEDIUM, "item speed");
        }
      }

//      if (slope[1] < 0.05) {
//        // taking items too quickly
//        Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
//          .forPlayer(player)
////          .withVL(MathHelper.minmax(15, averageSpeed * 0.75, 50))
//          .withVL(10)
//          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
//          .withMessage("is taking items too quickly")
//          .withDetails("linear d/t baseline at " + formatDouble(slope[1], 2) + " slots/sec")
//          .build();
//        ViolationProcessor violationProcessor = Modules.violationProcessor();
//        ViolationContext context = violationProcessor.processViolation(violation);
//
////        if (context.violationLevelAfter() > 100) {
////          user.nerf(AttackNerfStrategy.DMG_MEDIUM, "item speed");
////        }
//
////        System.out.println("is taking items too quickly" + "linear d/t baseline at " + formatDouble(slope[1], 2) + " slots/sec");
//      }

//      if (pearson > 0.5) {
//        Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
//          .forPlayer(player)
////          .withVL(MathHelper.minmax(15, averageSpeed * 0.75, 50))
//          .withVL(5)
//          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
//          .withMessage("seems to be taking items in a pattern")
//          .withDetails(formatDouble(pearson, 2) + " pear")
//          .build();
//        ViolationProcessor violationProcessor = Modules.violationProcessor();
//        violationProcessor.processViolation(violation);
////        System.out.println("seems to be taking items in a pattern" + formatDouble(pearson, 2) + " pear");
//      }

      if (averageSpeed > 20) {
        Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
          .forPlayer(player)
          .withVL(MathHelper.minmax(1, (averageSpeed/2) - 15, 50))
//          .withVL(1)
          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
          .withMessage("is taking items too quickly")
          .withDetails("at " + formatDouble(averageSpeed, 2) + " slots/sec")
          .build();
        ViolationProcessor violationProcessor = Modules.violationProcessor();
        ViolationContext context = violationProcessor.processViolation(violation);

        if (context.violationLevelAfter() > 100) {
          user.nerf(AttackNerfStrategy.DMG_MEDIUM, "item speed");
        }
      }
//      try (CSVExport csvExport = new CSVExport("click_history", "Distance", "Time")) {
//        for (double[] entry : clickHistory) {
//          csvExport.write(entry[0], entry[1]);
//        }
//      }
      // clear 50% of the history
      meta.distanceTime = meta.distanceTime.subList((int) (meta.distanceTime.size() * (2d / 3d)), meta.distanceTime.size());
      meta.slotTime = meta.slotTime.subList((int) (meta.slotTime.size() * (2d / 3d)), meta.slotTime.size());
    }
    meta.firstClickTimestamp = 0;
  }

  private boolean missingItemStack(WrapperPlayClientClickWindow.WindowClickType clickType) {
    return clickType == WrapperPlayClientClickWindow.WindowClickType.QUICK_MOVE ||
      clickType == WrapperPlayClientClickWindow.WindowClickType.SWAP;
  }

  private double[] slopeOfClicks(User user) {
    ClickDelayMeta meta = metaOf(user);
    List<double[]> clickHistory = meta.distanceTime;
    if (clickHistory.isEmpty()) {
      return new double[]{0, 0};
    }
    Matrix A = new Matrix(clickHistory.size(), 2);
    Matrix b = new Matrix(clickHistory.size(), 1);
    for (int i = 0; i < clickHistory.size(); i++) {
      A.set(i, 0, clickHistory.get(i)[0] / 10);
      A.set(i, 1, 1);
      b.set(i, 0, clickHistory.get(i)[1]);
    }
    Matrix x = A.pinv().multiply(b);
    return new double[]{x.get(0, 0), x.get(1, 0)};
  }

  private double[] clickTimeDistanceVariance(User user) {
    ClickDelayMeta meta = metaOf(user);
    List<double[]> clickHistory = meta.distanceTime;

    if (clickHistory.isEmpty()) {
      return new double[]{0, 0};
    }

    // treat distance time as separate variables x and y
    // find mean of x and y, calculate variance matrix
    double sumX = 0;
    double sumY = 0;
    for (double[] entry : clickHistory) {
      sumX += entry[0];
      sumY += entry[1];
    }
    double meanX = sumX / clickHistory.size();
    double meanY = sumY / clickHistory.size();

    double varX = 0;
    double varY = 0;
    double covarXY = 0;
    for (double[] entry : clickHistory) {
      varX += Math.pow(entry[0] - meanX, 2);
      varY += Math.pow(entry[1] - meanY, 2);
      covarXY += (entry[0] - meanX) * (entry[1] - meanY);
    }
    varX /= clickHistory.size();
    varY /= clickHistory.size();
    covarXY /= clickHistory.size();
    return new double[]{meanX, meanY, varX, varY, covarXY};
  }

  private double[][] slotsMovement(User user) {
    ClickDelayMeta meta = metaOf(user);
    List<double[]> clickHistory = meta.slotTime;
    List<double[]> distanceTime = meta.distanceTime;
    if (clickHistory.isEmpty()) {
      return new double[0][0];
    }

    double[][] movement = new double[clickHistory.size()][4];
    double[] velocity = new double[]{0, 0};
    int lastSlot = 13;

    double alpha = 0.8;
    for (int i = 0; i < clickHistory.size(); i++) {
      double[] slotTimePair = clickHistory.get(i);
      double[] distanceTimePair = distanceTime.get(i);

      double[] movementBetween = movementBetween(lastSlot, (int) slotTimePair[0]);

      double x = movementBetween[0];
      double y = movementBetween[1];

      // account for time
      double time = slotTimePair[1];
      movementBetween[0] /= time;
      movementBetween[1] /= time;

      velocity[0] = alpha * velocity[0] + (1 - alpha) * movementBetween[0];
      velocity[1] = alpha * velocity[1] + (1 - alpha) * movementBetween[1];
      movement[i] = new double[]{movementBetween[0], movementBetween[1], velocity[0], velocity[1]};
//      lastSlot = (int) slotTimePair[0];

//      user.player().sendMessage(
//        "x: " + formatDouble(x, 2) + "/" + formatDouble(movementBetween[0], 2) +
//          " y: " + formatDouble(y, 2) + "/" + formatDouble(movementBetween[1], 2) +
//          " vx: " + formatDouble(velocity[0], 2) +
//          " vy: " + formatDouble(velocity[1], 2) +
//          " d" + distanceTimePair[0] +
//          " t" + distanceTimePair[1]);
    }
    return movement;
  }

  private double pearsonCorrelation(User user) {
    ClickDelayMeta meta = metaOf(user);
    List<double[]> clickHistory = meta.distanceTime;
    if (clickHistory.isEmpty()) {
      return 0;
    }
    double sumX = 0;
    double sumY = 0;
    double sumXY = 0;
    double sumX2 = 0;
    double sumY2 = 0;
    for (double[] entry : clickHistory) {
      sumX += entry[0];
      sumY += entry[1];
      sumXY += entry[0] * entry[1];
      sumX2 += Math.pow(entry[0], 2);
      sumY2 += Math.pow(entry[1], 2);
    }
    double n = clickHistory.size();
    return (n * sumXY - sumX * sumY) / (Math.sqrt((n * sumX2 - Math.pow(sumX, 2)) * (n * sumY2 - Math.pow(sumY, 2))));
  }

  private double gaussMismatch(User user) {
    ClickDelayMeta meta = metaOf(user);
    List<double[]> clickHistory = meta.distanceTime;
    if (clickHistory.isEmpty()) {
      return 1;
    }
    long[] hist = new long[50];
    for (double[] entry : clickHistory) {
      int index = (int) (entry[0] * 10);
      if (index < 20) {
        hist[index]++;
      }
    }
    double mean = 0;
    double std = 0;
    for (int i = 0; i < hist.length; i++) {
      mean += hist[i] * i;
    }
    mean /= clickHistory.size();
    for (int i = 0; i < hist.length; i++) {
      std += Math.pow(i - mean, 2) * hist[i];
    }
    std = Math.sqrt(std / clickHistory.size());
    double chi2 = 0;
    for (int i = 0; i < hist.length; i++) {
      double dist = -Math.pow(i - mean, 2) / (2 * Math.pow(std, 2));
      double dist2 = Math.exp(dist) / (std * Math.sqrt(2 * Math.PI));
      chi2 += Math.pow(hist[i] - dist2, 2) / (dist2);
    }
    return chi2;
  }

  private void checkWindowClick(Player player, ClickDelayMeta meta, int slot) {
    double distance = distanceBetween(slot, meta.lastSlot);
    double time = (System.currentTimeMillis() - meta.lastClickedTimestamp) / 1000d;
    if (time > 2) {
      time = 2;
    }
    if (time == 0) {
      time = 0.01;
    }
    if (distance > 11) {
      distance = 11;
    }
//    player.sendMessage(formatDouble(distance, 2) + " slots in " + formatDouble(time, 2) + "s");
    meta.distanceTime.add(new double[]{distance, time});
    meta.slotTime.add(new double[]{slot, time});
  }

  private double distanceBetween(int slot1, int slot2) {
    int[] slot1Pos = slotToPosition(slot1);
    int[] slot2Pos = slotToPosition(slot2);
    return Math.sqrt(Math.pow(slot1Pos[0] - slot2Pos[0], 2) + Math.pow(slot1Pos[1] - slot2Pos[1], 2));
  }

  private double[] movementBetween(int slot1, int slot2) {
    int[] slot1Pos = slotToPosition(slot1);
    int[] slot2Pos = slotToPosition(slot2);
    return new double[]{slot2Pos[0] - slot1Pos[0], slot2Pos[1] - slot1Pos[1]};
  }

  private int[] slotToPosition(int slot) {
    int row = (slot / 9) + 1;
    int column = slot - ((row - 1) * 9);
    return new int[]{row, column};
  }

  public static final class ClickDelayMeta extends CheckCustomMetadata {
    private int lastSlot = -1;
    private Material lastClickedItemType = Material.AIR;
    private int lastContainerId = -1;
    private long lastClickedTimestamp = 0;
    private long firstClickTimestamp = 0;
    private boolean windowOpen = false;
    private long lastWindowOpenTimestamp = 0;

    private List<double[]> distanceTime = new ArrayList<>();
    private List<double[]> slotTime = new ArrayList<>();
  }
}
