package de.jpx3.intave.check.other.inventoryclickanalysis;

import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.InventoryClickAnalysis;
import de.jpx3.intave.math.Matrix;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationProcessor;
import de.jpx3.intave.packet.reader.WindowClickReader;
import de.jpx3.intave.packet.reader.WindowCloseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
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
  public void openWindowPacket(Player player, PacketContainer packet) {
    User user = userOf(player);
    ClickDelayMeta meta = metaOf(user);
    user.tickFeedback(() -> {
      meta.lastWindowOpenTimestamp = System.currentTimeMillis();
      meta.lastClickedTimestamp = System.currentTimeMillis();
      meta.firstClickTimestamp = 0;
      meta.lastSlot = 22;
    });
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      WINDOW_CLICK
    }
  )
  public void windowClickPacket(Player player, WindowClickReader windowClick) {
    if (player.getGameMode().equals(GameMode.CREATIVE)) {
      return;
    }
    User user = userOf(player);
    ClickDelayMeta meta = metaOf(user);
    int slot = windowClick.slot();
    ItemStack itemStack = windowClick.itemStack();
    Material clickedItemType = itemStack == null ? Material.AIR : itemStack.getType();
    boolean isDrop = windowClick.isDrop();
//    player.sendMessage("Clicked slot: " + slot + " with item: " + clickedItemType + " click type: " + windowClick.clickType() + " is drop: " + isDrop);
    if (slot != -999 && meta.lastSlot != -999) {
      if ((clickedItemType != meta.lastClickedItemType || isDrop || windowClick.missingItemStack()) && meta.lastClickedTimestamp != 0) {
        checkWindowClick(player, meta, slot);
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
  public void closeWindowPacket(Player player, WindowCloseReader closeReader) throws Exception {
    User user = userOf(player);
    ClickDelayMeta meta = metaOf(user);

//    player.sendMessage("Click history: " + meta.clickHistory.size() + " entries");

    if (meta.clickHistory.isEmpty()) {
      return;
    }

    if (meta.clickHistory.size() > 30) {
      List<double[]> clickHistory = meta.clickHistory;
      double sum = 0;
      for (double[] doubles : clickHistory) {
        sum += doubles[0] / doubles[1];
      }
      double averageSpeed = sum / clickHistory.size();
      double[] slope = slopeOfClicks(user);
      double pearson = pearsonCorrelation(user);

//      player.sendMessage("CI | "+(meta.firstClickTimestamp-meta.lastWindowOpenTimestamp)+"ms fc | " + MathHelper.formatDouble(averageSpeed, 2) + " s/s avg" + " | " + formatDouble(slope, 2) + " distance/time correlation");

      if (slope[0] < 0.2 || pearson < 0.2) {
        Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
          .forPlayer(player)
//          .withVL(slope < 0.05 ? 50 : 10)
          .withVL(10)
          .withMessage("seems indifferent to distance taking items")
          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
          .withDetails(formatDouble(slope[0], 2) + " d/t, " + formatDouble(pearson, 2) + " pear")
          .build();
        ViolationProcessor violationProcessor = Modules.violationProcessor();
        violationProcessor.processViolation(violation);
//        System.out.println("seems indifferent to distance taking items" + formatDouble(slope[0], 2) + " d/t, " + formatDouble(pearson, 2) + " pear");
      }

      if (slope[1] < 0.05) {
        // taking items too quickly
        Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
          .forPlayer(player)
//          .withVL(MathHelper.minmax(15, averageSpeed * 0.75, 50))
          .withVL(10)
          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
          .withMessage("is taking items too quickly")
          .withDetails("linear d/t baseline at " + formatDouble(slope[1], 2) + " slots/sec")
          .build();
        ViolationProcessor violationProcessor = Modules.violationProcessor();
        violationProcessor.processViolation(violation);
//        System.out.println("is taking items too quickly" + "linear d/t baseline at " + formatDouble(slope[1], 2) + " slots/sec");
      }

      if (pearson > 0.5) {
        Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
          .forPlayer(player)
//          .withVL(MathHelper.minmax(15, averageSpeed * 0.75, 50))
          .withVL(5)
          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
          .withMessage("seems to be taking items in a pattern")
          .withDetails(formatDouble(pearson, 2) + " pear")
          .build();
        ViolationProcessor violationProcessor = Modules.violationProcessor();
        violationProcessor.processViolation(violation);
//        System.out.println("seems to be taking items in a pattern" + formatDouble(pearson, 2) + " pear");
      }

      if (averageSpeed > 20) {
        Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
          .forPlayer(player)
//          .withVL(MathHelper.minmax(15, averageSpeed * 0.75, 50))
          .withVL(5)
          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
          .withMessage("is taking items too quickly")
          .withDetails("at " +formatDouble(averageSpeed, 2) + " slots/sec")
          .build();
        ViolationProcessor violationProcessor = Modules.violationProcessor();
        violationProcessor.processViolation(violation);
//        System.out.println("is taking items too quickly" + "at " +formatDouble(averageSpeed, 2) + " slots/sec");
      }

//      try (CSVExport csvExport = new CSVExport("click_history", "Distance", "Time")) {
//        for (double[] entry : clickHistory) {
//          csvExport.write(entry[0], entry[1]);
//        }
//      }
      // clear 50% of the history
      meta.clickHistory = meta.clickHistory.subList((int) (meta.clickHistory.size() * (2d/3d)), meta.clickHistory.size());
    }
    meta.firstClickTimestamp = 0;
  }

  private double[] slopeOfClicks(User user) {
    ClickDelayMeta meta = metaOf(user);
    List<double[]> clickHistory = meta.clickHistory;
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

  private double pearsonCorrelation(User user) {
    ClickDelayMeta meta = metaOf(user);
    List<double[]> clickHistory = meta.clickHistory;
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
    List<double[]> clickHistory = meta.clickHistory;
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
    meta.clickHistory.add(new double[]{distance, time});
//    player.sendMessage(distance + " slots in " + time + " seconds, thats " + (distance / time) + " slots per second");
  }

  private double distanceBetween(int slot1, int slot2) {
    int[] slot1Pos = slotToPosition(slot1);
    int[] slot2Pos = slotToPosition(slot2);
    return Math.sqrt(Math.pow(slot1Pos[0] - slot2Pos[0], 2) + Math.pow(slot1Pos[1] - slot2Pos[1], 2));
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
    private long lastWindowOpenTimestamp = 0;

    private List<double[]> clickHistory = new ArrayList<>();
  }
}
