package de.jpx3.intave.access.check;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public interface CheckAccess {
  String name();
  boolean enabled();

  default double violationLevelOf(Player player) {
    return violationLevelOf(player, "thresholds");
  }
  double violationLevelOf(Player player, String threshold);

  default void addViolationPoints(Player player, double amount) {
    addViolationPoints(player, "thresholds", amount);
  }
  void addViolationPoints(Player player, String threshold, double amount);

  default void resetViolationLevel(Player player) {
    resetViolationLevel(player, "thresholds");
  }
  void resetViolationLevel(Player player, String threshold);

  Map<Integer, List<String>> commandsOf(String threshold);
  CheckStatisticsAccess statistics();
}